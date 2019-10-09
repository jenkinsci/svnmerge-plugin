package jenkins.plugins.svnmerge;

import hudson.AbortException;
import hudson.model.AbstractProject;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Queue.Task;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.scm.SubversionTagAction;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.wc.SVNInfo;

import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractProject}-level action to rebase changes from the upstream branch into the current branch.
 *
 * @author Kohsuke Kawaguchi
 */
public class RebaseAction extends AbstractSvnmergeTaskAction<RebaseSetting> {

     protected Permission getPermission() {
         return REBASE_PERMISSION;
     }
	
    public final AbstractProject<?,?> project;

    public RebaseAction(AbstractProject<?,?> project) {
        this.project = project;
    }

    public String getIconFileName() {
        if(!isApplicable()) return null; // missing configuration
        return "/plugin/svnmerge/24x24/sync.gif";
    }

    public String getDisplayName() {
        return "Rebase From Upstream";
    }

    public String getUrlName() {
        return "rebase-branch";
    }

    protected ACL getACL() {
        return project.getACL();
    }

    @Override
    public AbstractProject<?, ?> getProject() {
        return project;
    }

    /**
     * Do we have enough information to perform rebase?
     * If not, we need to pretend as if this action is not here.
     */
    private boolean isApplicable() {
        return getProperty()!=null;
    }

    public File getLogFile() {
        return new File(project.getRootDir(),"rebase.log");
    }

    protected RebaseSetting createParams(StaplerRequest req) {
        String id = req.getParameter("permalink");
        if (id!=null)   return new RebaseSetting(id);
        else            return new RebaseSetting(-1);
    }

    @Override
    protected TaskImpl createTask(RebaseSetting param) throws IOException {
        return new RebaseTask(param);
    }

    /**
     * Does the rebase.
     * <p>
     * This requires that the calling thread owns the workspace.
     */
    /*package*/ List<Long> perform(TaskListener listener, RebaseSetting param) throws IOException, InterruptedException {
        List<Long> revisions = new ArrayList<Long>();
        revisions.add(param.revision);

        if (param.permalink!=null) {
            AbstractProject<?, ?> up = getProperty().getUpstreamProject();
            Permalink p = up.getPermalinks().get(param.permalink);
            if (p!=null) {
                Run<?,?> b = p.resolve(up);
                if (b==null) {
                    listener.getLogger().println("No build that matches "+p.getDisplayName()+". Rebase is no-nop.");
                    return null;
                }

                SubversionTagAction a = b.getAction(SubversionTagAction.class);
                if (a==null)
                    throw new AbortException("Unable to determine the Subversion revision number from "+b.getFullDisplayName());

                revisions = new ArrayList<Long>();
                for(SvnInfo sv : a.getTags().keySet()){
                    long currentRev = sv.revision;
                    revisions.add(currentRev);
                }
            }
        }

        List<Long> integratedRevisions = new ArrayList<Long>();
        for(Long rev : revisions){
            List<Long> integrated = getProperty().rebase(listener, rev);
            if(integrated != null)
                integratedRevisions.addAll(integrated);
            else
                return null;
        }
//        if(integratedRevision>0) {
//            // record this integration as a fingerprint.
//            // this will allow us to find where this change is integrated.
//            Jenkins.getInstance().getFingerprintMap().getOrCreate(
//                    build, IntegrateAction.class.getName(),
//                    getFingerprintKey());
//        }
        return integratedRevisions;
    }

    /**
     * Cancels a rebase task in the queue, if any.
     */
    public void doCancelQueue(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        project.checkPermission(AbstractProject.BUILD);
        Jenkins.getInstance().getQueue().cancel(new RebaseTask(null));
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Which page to render?
     */
    protected String decidePage() {
        if (workerThread != null)   return "inProgress.jelly";
        return "form.jelly";
    }


    /**
     * {@link Task} that performs the integration.
     */
    private class RebaseTask extends TaskImpl {
        private RebaseTask(RebaseSetting param) throws IOException {
            super(param);
        }

        public String getFullDisplayName() {
            return "Rebasing "+getProject().getFullDisplayName();
        }

        public String getDisplayName() {
            return "Rebasing "+getProject().getDisplayName();
        }
    }

    public static final String COMMIT_MESSAGE_PREFIX = "[REBASE] ";
}
