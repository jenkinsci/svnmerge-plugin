package jenkins.plugins.svnmerge;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RebaseBuilder extends Builder {
    /**
     * {@link Permalink#getId() id} of the permalink to rebase with.
     */
    public final String permalink;

    @DataBoundConstructor
    public RebaseBuilder(String permalink) {
        this.permalink = permalink;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        new RebaseAction(build.getProject()).perform(listener,new RebaseSetting(permalink));
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Perform svn merge from upstream branch";
        }

        public ListBoxModel doFillPermalinkItems(@AncestorInPath AbstractProject prj) {
            ListBoxModel r = new ListBoxModel();
            r.add("Latest revision","(default)");
            for (Permalink p : prj.getPermalinks()) {
                r.add(p.getDisplayName(),p.getId());
            }
            return r;
        }
    }
}
