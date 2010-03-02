package hudson.plugins.svnmerge;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Fingerprint;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TopLevelItem;
import hudson.scm.ChangeLogSet.Entry;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * {@link JobProperty} that marks projects that accept feature branch
 * integrations from {@link FeatureBranchProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class IntegratableProject extends JobProperty<AbstractProject<?,?>> {
    @Override
    public Action getJobAction(AbstractProject<?,?> _) {
        return new IntegratableProjectAction(this);
    }

    /**
     * If a build is picking up an integration, record this build into a fingerprint
     * (so that we can track where we've integrated changes.)
     */
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        for (Entry e : build.getChangeSet()) {
            Fingerprint f = IntegrateAction.getIntegrationFingerprint(e);
            // this build is merging an integration. Leave this in the record
            if(f!=null)
                f.add(build);
        }

        return true;
    }

    public AbstractProject<?,?> getOwner() {
        return owner;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        /**
         * For us to copy a job, we need this to be {@link TopLevelItem}.
         */
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return TopLevelItem.class.isAssignableFrom(jobType);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if(!formData.has("svnmerge_integratable"))   return null;
            return new IntegratableProject();
        }

        public String getDisplayName() {
            return "Integratable Project";
        }
    }
}
