package jenkins.plugins.svnmerge;

import static jenkins.plugins.svnmerge.Utility.rootBuildOf;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class RebaseBuilder extends Builder {
    /**
     * {@link Permalink#getId() id} of the permalink to rebase with.
     */
    public final String permalink;
    /**
     * Indicates whether to stop the build if the merge fails. 
     */
    public final boolean stopBuildIfMergeFails;
    public final boolean setUnstableIfMergeFails;

    @DataBoundConstructor
    public RebaseBuilder(String permalink, boolean stopBuildIfMergeFails, boolean setUnstableIfMergeFails) {
        this.permalink = permalink;
        this.stopBuildIfMergeFails = stopBuildIfMergeFails;
        this.setUnstableIfMergeFails = setUnstableIfMergeFails;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //JENKINS-25769 If this is a promotion build, then we need to get the rootBuild
        AbstractProject<?,?> project = rootBuildOf(build).getProject();
        FeatureBranchProperty property = project.getProperty(FeatureBranchProperty.class);

        if (property == null) {
            listener.getLogger().println("Project does not build a Subversion feature branch. Skip rebase action.");
            return true;
        }

    	RebaseAction rebaseAction = new  RebaseAction(project);
    	List<Long> results = rebaseAction.perform(listener,new RebaseSetting(permalink));
        boolean buildStable = true;

        if(results != null && results.size() > 0){
            for(Long result : results){
                if(result<0){
                    build.setResult(Result.UNSTABLE);
                    buildStable = false;
                }
            }
        } else {
            buildStable = false;
        }

        return !stopBuildIfMergeFails || buildStable;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Rebase with upstream Subversion revision";
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
