package jenkins.plugins.svnmerge;

import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.ResourceList;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import hudson.remoting.AsyncFutureImpl;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.LargeText;

import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 * @param <P>
 *          Type that represents the parameter of the action
 */
public abstract class AbstractSvnmergeTaskAction<P> extends TaskAction {
	 public static final PermissionGroup PERMISSIONS = new PermissionGroup(AbstractSvnmergeTaskAction.class, Messages._PermissionGroupName());
     public static final Permission REBASE_PERMISSION = new Permission(PERMISSIONS,"Rebase",Messages._RebaseAction_PermissionDesc(),null, PermissionScope.ITEM);
     public static final Permission INTEGRATE_PERMISSION = new Permission(PERMISSIONS,"Integrate",Messages._IntegrateAction_PermissionDesc(),null, PermissionScope.ITEM);

	
	/*package*/ AbstractSvnmergeTaskAction() { // subtyping only allowed for this plugin
    }

    public abstract AbstractProject<?,?> getProject();

    public final FeatureBranchProperty getProperty() {
        return getProject().getProperty(FeatureBranchProperty.class);
    }

    @Override
    public LargeText getLog() {
        return new LargeText(getLogFile(),workerThread==null);
    }

    public abstract File getLogFile();

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this, decidePage()).forward(req,rsp);
    }

    /**
     * Schedules the execution of this task.
     *
     * <p>
     * This happens asynchronously.
     */
    public Future<WorkerThread> performAsync(P param) throws IOException {
        getACL().checkPermission(getPermission());
        TaskImpl task = createTask(param);
        Jenkins.getInstance().getQueue().schedule(task, 0);
        return task.future;
    }

    protected abstract TaskImpl createTask(P param) throws IOException;

    protected abstract P createParams(StaplerRequest req) throws IOException;

    /**
     * Called from UI to commence this task.
     */
    public synchronized void doPerform(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        performAsync(createParams(req));
        rsp.sendRedirect(".");
    }

    /**
     * Synchronously execute the task.
     * <p>
     * This requires that the calling thread owns the workspace.
     */
    /*package*/ abstract long perform(TaskListener listener, P param) throws IOException, InterruptedException;

    /**
     * Which page to render in the top page?
     */
    protected abstract String decidePage();

    public final class WorkerThread extends TaskThread {
        public final P param;

        public WorkerThread(P param) throws IOException {
            super(AbstractSvnmergeTaskAction.this, ListenerAndText.forFile(getLogFile(),AbstractSvnmergeTaskAction.this));
            associateWith(AbstractSvnmergeTaskAction.this);
            this.param = param;
        }

        protected void perform(TaskListener listener) throws Exception {
            AbstractSvnmergeTaskAction.this.perform(listener,param);
        }
    }

    /**
     * {@link Task} that performs the integration.
     */
    protected abstract class TaskImpl extends AbstractQueueTask {
        private final AsyncFutureImpl<WorkerThread> future = new AsyncFutureImpl<WorkerThread>();
        private final WorkerThread thread;

        public TaskImpl(P param) throws IOException {
            // do this now so that this gets tied with the action.
            thread = new WorkerThread(param);
        }

        public boolean isConcurrentBuild() {
            return false;
        }

        public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        public Object getSameNodeConstraint() {
            return null;
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
        public long getEstimatedDuration() {
            return -1;
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            return new Queue.Executable() {
                public Queue.Task getParent() {
                    return TaskImpl.this;
                }

                public long getEstimatedDuration() {
                    return TaskImpl.this.getEstimatedDuration();
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

        /**
         * Exclusive access to the workspace required.
         */
        @Override
        public ResourceList getResourceList() {
            return new ResourceList().w(getProject().getWorkspaceResource());
        }

        private AbstractProject<?,?> getProject() {
            return AbstractSvnmergeTaskAction.this.getProject();
        }

        @Override
        public int hashCode() {
            return getProject().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AbstractSvnmergeTaskAction.TaskImpl) {
                TaskImpl that = (TaskImpl) obj;
                return this.getProject()==that.getProject();
            }
            return false;
        }

        public String getUrl() {
            return getProject().getUrl()+getUrlName();
        }

        public void doCancelQueue(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            TaskImpl.this.doCancelQueue(req,rsp);
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
            // XXX Jenkins.getInstance().getAuthorizationStrategy().getACL(...).hasPermission(...)
            return true;
        }
    }
}
