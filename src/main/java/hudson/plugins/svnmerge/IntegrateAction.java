package hudson.plugins.svnmerge;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.Fingerprint;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.remoting.AsyncFutureImpl;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.scm.SubversionTagAction;
import hudson.security.ACL;
import hudson.security.Permission;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.LargeText;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.acegisecurity.AccessDeniedException;

/**
 * {@link AbstractBuild}-level action to integrate
 * the build to upstream branch.
 *
 * @author Kohsuke Kawaguchi
 */
public class IntegrateAction extends TaskAction implements BuildBadgeAction {
    public final AbstractBuild<?,?> build;

    /**
     * If the integration is successful, set to the revision of the commit of the merge.
     * If the integration is successful but there was nothing to merge, 0.
     * If it failed, -1. If an integration was never attempted, null.
     */
    private Long integratedRevision;

    public IntegrateAction(AbstractBuild<?,?> build) {
        this.build = build;
    }

    public String getIconFileName() {
        if(!isApplicable()) return null; // missing configuration
        return "/plugin/svnmerge/24x24/integrate.gif";
    }

    public String getDisplayName() {
        return "Integrate Branch";
    }

    public String getUrlName() {
        return "integrate-branch";
    }

    protected Permission getPermission() {
        return Item.CONFIGURE;
    }

    protected ACL getACL() {
        return build.getACL();
    }

    /**
     * Do we have enough information to perform integration?
     * If not, we need to pretend as if this action is not here.
     */
    private boolean isApplicable() {
        return getSvnInfo()!=null && getProperty()!=null;
    }

    public FeatureBranchProperty getProperty() {
        return build.getProject().getProperty(FeatureBranchProperty.class);
    }

    public boolean isIntegrated() {
        return integratedRevision!=null && integratedRevision>=0;
    }

    public boolean isIntegrationAttempted() {
        return integratedRevision!=null;
    }

    public Long getIntegratedRevision() {
        return integratedRevision;
    }

    @Override
    public LargeText getLog() {
        return new LargeText(getLogFile(),workerThread==null);
    }

    private File getLogFile() {
        return new File(build.getRootDir(),"integrate.log");
    }

    /**
     * URL and revision to be integrated from this action.
     */
    public SvnInfo getSvnInfo() {
        SubversionTagAction sta = build.getAction(SubversionTagAction.class);
        if(sta==null)   return null;
        Map<SvnInfo,List<String>> tags = sta.getTags();
        if(tags.size()!=1)  return null;    // can't handle more than 1 URLs
        return tags.keySet().iterator().next();
    }

    /**
     * Integrate the branch.
     * <p>
     * This requires that the calling thread owns the workspace.
     */
    /*package*/ long integrate(TaskListener listener) throws IOException, InterruptedException {
        SvnInfo si = getSvnInfo();
        String commitMessage = getCommitMessage();
        integratedRevision = getProperty().integrate(listener, si.url, si.revision, commitMessage);
        if(integratedRevision>0) {
            // record this integration as a fingerprint.
            // this will allow us to find where this change is integrated.
            Hudson.getInstance().getFingerprintMap().getOrCreate(
                    build, IntegrateAction.class.getName(),
                    getFingerprintKey());
        }
        build.save();
        return integratedRevision;
    }

    /**
     * Gets the build number of the upstream where this integration is built.
     *
     * <p>
     * Since the relevant information might be already lost when this method
     * is called, this code needs to be defensive.
     *
     * @return -1
     *      if not integrated yet or this information is lost.
     */
    public int getUpstreamBuildNumber() throws IOException {
        Fingerprint f = Hudson.getInstance().getFingerprintMap().get(getFingerprintKey());
        if(f==null)         return -1;
        FeatureBranchProperty p = getProperty();
        RangeSet rs = new RangeSet(); // empty range set
        if(p!=null)
            rs = f.getRangeSet(p.getUpstreamProject());
        else {
            // we don't know for sure what is our upstream project.
            Hashtable<String,RangeSet> usages = f.getUsages();
            if(!usages.isEmpty())
                rs = usages.values().iterator().next();
        }
        if(rs.isEmpty())    return -1;

        return rs.min();
    }

    /**
     * This is the md5 hash to keep track of where this change is integrated.
     */
    public String getFingerprintKey() {
        return Util.getDigestOf(getCommitMessage()+"#"+integratedRevision);
    }

    private String getCommitMessage() {
        return COMMIT_MESSAGE_PREFIX + build.getFullDisplayName()+ COMMIT_MESSAGE_SUFFIX;
    }

    /**
     * Schedules the integration of this branch to the upstream.
     *
     * <p>
     * This happens asynchronously.
     */
    public Future<WorkerThread> integrateAsync() throws IOException {
        getACL().checkPermission(getPermission());
        IntegrateAction.IntegrationTask task = new IntegrationTask();
        Hudson.getInstance().getQueue().add(task, 0);
        return task.future;
    }

    public synchronized void doIntegrate(StaplerResponse rsp) throws IOException, ServletException {
        integrateAsync();
        rsp.sendRedirect(".");
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this, decidePage()).forward(req,rsp);
    }

    /**
     * Cancels an integration task in the queue, if any.
     */
    public void doCancelQueue(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        build.getProject().checkPermission(AbstractProject.BUILD);
        Hudson.getInstance().getQueue().cancel(new IntegrationTask());
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Which page to render?
     */
    private String decidePage() {
        if(isIntegrated())          return "completed.jelly";
        if (workerThread != null)   return "inProgress.jelly";
        return "form.jelly";
    }

    public final class WorkerThread extends TaskThread {
        public WorkerThread() throws IOException {
            super(IntegrateAction.this, ListenerAndText.forFile(getLogFile()));
            associateWith(IntegrateAction.this);
        }

        protected void perform(TaskListener listener) throws Exception {
            integrate(listener);
        }
    }

    /**
     * {@link Task} that performs the integration.
     */
    private class IntegrationTask implements Queue.Task {
        private final AsyncFutureImpl<WorkerThread> future = new AsyncFutureImpl<WorkerThread>();
        private final WorkerThread thread;

        public IntegrationTask() throws IOException {
            // do this now so that this gets tied with the action.
            thread = new WorkerThread();
        }

        /**
         * This has to run on the last workspace.
         */
        @Override
        public Label getAssignedLabel() {
            Node node = getLastBuiltOn();
            return node != null ? node.getSelfLabel() : null;
        }

        @Override
        public String getFullDisplayName() {
            return getProject().getFullDisplayName()+" Integration";
        }

        @Override
        public long getEstimatedDuration() {
            return -1;
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            return new Queue.Executable() {
                public Queue.Task getParent() {
                    return IntegrationTask.this;
                }

                public void run() {
                    // run this synchronously
                    try {
                        thread.run();
                    } finally {
                        future.set(thread);
                    }
                }
            };
        }

        @Override
        public String getDisplayName() {
            return getProject().getDisplayName()+" Integration";
        }

        /**
         * Exclusive access to the workspace required.
         */
        @Override
        public ResourceList getResourceList() {
            return new ResourceList().w(getProject().getWorkspaceResource());
        }

        private AbstractProject<?,?> getProject() {
            return build.getProject();
        }

        @Override
        public int hashCode() {
            return getProject().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof IntegrationTask) {
                IntegrationTask that = (IntegrationTask) obj;
                return this.getProject()==that.getProject();
            }
            return false;
        }

        public String getUrl() {
            return getProject().getUrl()+getUrlName();
        }

        public void doCancelQueue(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            IntegrateAction.this.doCancelQueue(req,rsp);
        }

        public Node getLastBuiltOn() {
            return null;
        }

        public boolean isBuildBlocked() {
            return false;
        }

        public String getWhyBlocked() {
            return null;
        }

        public String getName() {
            return getDisplayName();
        }

        public void checkAbortPermission() {
            if (!hasAbortPermission()) {
                throw new AccessDeniedException("???");
            }
        }

        public boolean hasAbortPermission() {
            // XXX Hudson.getInstance().getAuthorizationStrategy().getACL(...).hasPermission(...)
            return true;
        }
    }

    /**
     * Checks if the given {@link Entry} represents a commit from
     * {@linkplain #integrate(TaskListener) integration}. If so,
     * return its fingerprint.
     *
     * Otherwise null.
     */
    public static Fingerprint getIntegrationFingerprint(Entry changeEntry) throws IOException {
        if (changeEntry instanceof LogEntry) {
            LogEntry le = (LogEntry) changeEntry;
            String msg = changeEntry.getMsg().trim();
            if(msg.startsWith(COMMIT_MESSAGE_PREFIX) && msg.endsWith(COMMIT_MESSAGE_SUFFIX)) {
                // this build is merging an integration. Leave this in the record
                return Hudson.getInstance().getFingerprintMap().get(Util.getDigestOf(msg + "#" + le.getRevision()));
            }
        }
        return null;
    }

    // used to find integration commits
    static final String COMMIT_MESSAGE_PREFIX = "Integrated ";
    static final String COMMIT_MESSAGE_SUFFIX = " (from Hudson)";
}
