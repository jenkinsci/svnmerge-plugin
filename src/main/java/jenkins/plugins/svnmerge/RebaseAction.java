package jenkins.plugins.svnmerge;

import hudson.model.AbstractProject;
import hudson.model.Queue.Task;
import hudson.model.TaskListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * {@link AbstractProject}-level action to rebase changes from the upstream branch into the current branch.
 *
 * @author Kohsuke Kawaguchi
 */
public class RebaseAction extends AbstractSvnmergeTaskAction {
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

    protected File getLogFile() {
        return new File(project.getRootDir(),"rebase.log");
    }

    @Override
    protected TaskImpl createTask() throws IOException {
        return new RebaseTask();
    }

    /**
     * Does the rebase.
     * <p>
     * This requires that the calling thread owns the workspace.
     */
    /*package*/ long perform(TaskListener listener) throws IOException, InterruptedException {
        long integratedRevision = getProperty().rebase(listener, -1);
//        if(integratedRevision>0) {
//            // record this integration as a fingerprint.
//            // this will allow us to find where this change is integrated.
//            Jenkins.getInstance().getFingerprintMap().getOrCreate(
//                    build, IntegrateAction.class.getName(),
//                    getFingerprintKey());
//        }
        return integratedRevision;
    }

    /**
     * Cancels a rebase task in the queue, if any.
     */
    public void doCancelQueue(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        project.checkPermission(AbstractProject.BUILD);
        Jenkins.getInstance().getQueue().cancel(new RebaseTask());
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
        private RebaseTask() throws IOException {
        }

        @Override
        public String getFullDisplayName() {
            return "Rebasing "+getProject().getFullDisplayName();
        }

        @Override
        public String getDisplayName() {
            return "Rebasing "+getProject().getDisplayName();
        }
    }
}
