package jenkins.plugins.svnmerge;

import static jenkins.plugins.svnmerge.Utility.rootBuildOf;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * {@link Publisher} that integrates the current workspace to the upstream. 
 *
 * @author Kohsuke Kawaguchi
 */
public class IntegrationPublisher extends Publisher {
    @DataBoundConstructor
    public IntegrationPublisher() {
    }

    /**
     * Running multiple merge concurrently is only going to result in conflicts, so no point in doing that.
     * So request that we run this step from different builds sequentially
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        // we only integrateAsync successful builds
        if(build.getResult().isWorseThan(Result.SUCCESS))
            return true;

        //JENKINS-14725 If this is a promotion build, then we need to get the rootBuild
        IntegrateAction ia = rootBuildOf(build).getAction(IntegrateAction.class);
        if(ia==null) {
            listener.getLogger().println("Upstream Subversion URL is not specified. Configuration problem?");
            return false;
        }

        if(ia.perform(listener,new IntegrateSetting())<0)
            build.setResult(Result.FAILURE);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return "Integrate to upstream upon successful build";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/svnmerge/help/auto-merge.html";
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
