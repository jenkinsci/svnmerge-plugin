package hudson.plugins.svnmerge;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
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

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        // we only integrateAsync successful builds
        if(build.getResult().isWorseThan(Result.SUCCESS))
            return true;

        IntegrateAction ia = build.getAction(IntegrateAction.class);
        if(ia==null) {
            listener.getLogger().println("Upstream Subversion URL is not specified. Configuration problem?");
            return false;
        }
        
        if(ia.integrate(listener)<0)
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
