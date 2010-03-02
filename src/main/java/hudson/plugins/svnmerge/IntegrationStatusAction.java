package hudson.plugins.svnmerge;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import hudson.model.Run;

import java.util.Collections;
import java.util.List;

/**
 * Project-level {@link Action} that shows the integration status on a feature branch job.
 *
 * <p>
 * This also adds a permalink to {@link AbstractProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class IntegrationStatusAction implements PermalinkProjectAction {
    public final AbstractProject<?,?> project;
    public final FeatureBranchProperty branchProperty;

    public IntegrationStatusAction(FeatureBranchProperty fbp) {
        this.project = fbp.getOwner();
        this.branchProperty = fbp;
    }

    /**
     * Finds the last build that got integrated to the upstream, or else null.
     */
    public AbstractBuild<?,?> getLastIntegratedBuild() {
        IntegrateAction ia = getLastIntegrateAction(project);
        return ia!=null ? ia.build : null;
    }

    public IntegrateAction getLastIntegrateAction() {
        return getLastIntegrateAction(project);
    }

    private static IntegrateAction getLastIntegrateAction(Job<?,?> j) {
        for(Run<?,?> b=j.getLastBuild(); b!=null; b=b.getPreviousBuild()) {
            IntegrateAction ia = b.getAction(IntegrateAction.class);
            if(ia!=null && ia.isIntegrated())
                return ia;
        }
        return null;
    }

    public List<Permalink> getPermalinks() {
        return PERMALINKS;
    }

    public String getIconFileName() {
        return "/plugin/svnmerge/24x24/integrate.gif";
    }

    public String getDisplayName() {
        return "Integration Status";
    }

    public String getUrlName() {
        return "integration-status";
    }

    private static final List<Permalink> PERMALINKS = Collections.<Permalink>singletonList(new Permalink() {
        public String getDisplayName() {
            return "Last Integrated build";
        }

        @Override
        public String getId() {
            return "lastIntegratedBuild";
        }

        @Override
        public Run<?,?> resolve(Job<?,?> job) {
            IntegrateAction ia = getLastIntegrateAction(job);
            return ia!=null ? ia.build : null;
        }
    });
}
