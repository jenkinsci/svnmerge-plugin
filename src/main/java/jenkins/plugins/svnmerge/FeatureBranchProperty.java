package jenkins.plugins.svnmerge;

import hudson.Extension;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SubversionEventHandlerImpl;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.util.IOException2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.tmatesoft.svn.core.SVNDepth.*;
import static org.tmatesoft.svn.core.wc.SVNRevision.*;

/**
 * {@link JobProperty} for feature branch projects.
 * <p>
 * This associates the upstream project (with {@link IntegratableProject} with this project.
 *
 * @author Kohsuke Kawaguchi
 */
public class FeatureBranchProperty extends JobProperty<AbstractProject<?,?>> {
    /**
     * Upstream job name.
     */
    private String upstream;
    private transient RebaseAction rebaseAction;

    @DataBoundConstructor
    public FeatureBranchProperty(String upstream) {
        if (upstream == null) {
            throw new NullPointerException("upstream");
        }
        this.upstream = upstream;
    }

    public String getUpstream() {
        return upstream;
    }

    /**
     * Gets the upstream project, or null if no such project was found.
     */
    public AbstractProject<?,?> getUpstreamProject() {
        return Jenkins.getInstance().getItemByFullName(upstream,AbstractProject.class);
    }

    public ModuleLocation getUpstreamSubversionLocation() {
        AbstractProject<?,?> p = getUpstreamProject();
        if(p==null)     return null;
        SCM scm = p.getScm();
        if (scm instanceof SubversionSCM) {
            SubversionSCM svn = (SubversionSCM) scm;
            return svn.getLocations()[0];
        }
        return null;
    }

    /**
     * Gets the {@link #getUpstreamSubversionLocation()} as {@link SVNURL}
     */
    public SVNURL getUpstreamURL() throws SVNException {
        ModuleLocation location = getUpstreamSubversionLocation();
        if(location==null)  return null;
        return location.getSVNURL();
    }
    
    public AbstractProject<?,?> getOwner() {
        return owner;
    }

    @Override
    public List<Action> getJobActions(AbstractProject<?,?> project) {
        if (rebaseAction==null)
            rebaseAction = new RebaseAction(project);
        return Arrays.asList(new IntegrationStatusAction(this), rebaseAction);
    }

    /**
     * Just add the integration action.
     */
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        build.addAction(new IntegrateAction(build));
        return true;
    }

    /**
     * Integrates changes made in the upstream into the branch at the workspace.
     *
     * <p>
     * This computation uses the workspace of the project. First, we update the workspace
     * to the tip of the branch (or else the commit will fail later), merge the changes
     * from the upstream, then commit it. If the merge fails, we'll revert the workspace
     * so that the next build can go smoothly.
     *
     * @param listener
     *      Where the progress is sent.
     * @param upstreamRev
     *      Revision of the branch to be integrated to the upstream.
     *      If -1, use the latest.
     * @return
     *      the new revision number if the rebase was successful.
     *      -1 if it failed and the failure was handled gracefully
     *      (typically this means a merge conflict.)
     */
    public long rebase(final TaskListener listener, final long upstreamRev) throws IOException, InterruptedException {
        final ISVNAuthenticationProvider provider = Jenkins.getInstance().getDescriptorByType(
                SubversionSCM.DescriptorImpl.class).createAuthenticationProvider();
        return owner.getModuleRoot().act(new FileCallable<Long>() {
            public Long invoke(File mr, VirtualChannel virtualChannel) throws IOException {
                try {
                    final PrintStream logger = listener.getLogger();
                    final boolean[] foundConflict = new boolean[1];
                    ISVNEventHandler printHandler = new SubversionEventHandlerImpl(logger,mr) {
                        @Override
                        public void handleEvent(SVNEvent event, double progress) throws SVNException {
                            super.handleEvent(event, progress);
                            if(event.getContentsStatus()== SVNStatusType.CONFLICTED)
                                foundConflict[0] = true;
                        }
                    };

                    SVNURL up = getUpstreamURL();
                    SVNClientManager cm = SubversionSCM.createSvnClientManager(provider);
                    cm.setEventHandler(printHandler);

                    SVNWCClient wc = cm.getWCClient();
                    SVNDiffClient dc = cm.getDiffClient();

                    logger.printf("Updating workspace to the latest revision\n");
                    long wsr = cm.getUpdateClient().doUpdate(mr, HEAD, INFINITY, false, false);
                    logger.printf("  Updated to rev.%d\n",wsr);

                    SVNRevision mergeRev = upstreamRev >= 0 ? SVNRevision.create(upstreamRev) : HEAD;

                    logger.printf("Merging change from the upstream %s\n",up);
                    dc.doMergeReIntegrate(up, mergeRev, mr, false);
                    if(foundConflict[0]) {
                        logger.println("Found conflict. Reverting this failed merge");
                        wc.doRevert(new File[]{mr},INFINITY, null);
                        return -1L;
                    } else {
                        logger.println("Committing changes");
                        SVNCommitClient cc = cm.getCommitClient();
                        SVNCommitInfo ci = cc.doCommit(new File[]{mr}, false, "Rebasing from "+up, null, null, false, false, INFINITY);
                        if(ci.getNewRevision()<0) {
                            logger.println("  No changes since the last integration");
                            return 0L;
                        } else {
                            logger.println("  committed revision "+ci.getNewRevision());
                            return ci.getNewRevision();
                        }
                    }
                } catch (SVNException e) {
                    throw new IOException2("Failed to merge", e);
                }
            }
        });
    }

    /**
     * Perform a merge to the upstream that integrates changes in this branch.
     *
     * <p>
     * This computation uses the workspace of the project.
     *
     * @param listener
     *      Where the progress is sent.
     * @param branchURL
     *      URL of the branch to be integrated. If null, use the workspace URL.
     * @param branchRev
     *      Revision of the branch to be integrated to the upstream.
     *      If -1, use the current workspace revision.
     * @return
     *      the new revision number if the integration was successful.
     *      -1 if it failed and the failure was handled gracefully
     *      (typically this means a merge conflict.) 
     */
    public long integrate(final TaskListener listener, final String branchURL, final long branchRev, final String commitMessage) throws IOException, InterruptedException {
        final ISVNAuthenticationProvider provider = Jenkins.getInstance().getDescriptorByType(
                SubversionSCM.DescriptorImpl.class).createAuthenticationProvider();
        return owner.getModuleRoot().act(new FileCallable<Long>() {
            public Long invoke(File mr, VirtualChannel virtualChannel) throws IOException {
                try {
                    final PrintStream logger = listener.getLogger();
                    final boolean[] foundConflict = new boolean[1];
                    ISVNEventHandler printHandler = new SubversionEventHandlerImpl(logger,mr) {
                        @Override
                        public void handleEvent(SVNEvent event, double progress) throws SVNException {
                            super.handleEvent(event, progress);
                            if(event.getContentsStatus()== SVNStatusType.CONFLICTED)
                                foundConflict[0] = true;
                        }
                    };

                    SVNURL up = getUpstreamURL();
                    SVNClientManager cm = SubversionSCM.createSvnClientManager(provider);
                    cm.setEventHandler(printHandler);

                    // capture the working directory state before the switch
                    SVNWCClient wc = cm.getWCClient();
                    SVNInfo wsState = wc.doInfo(mr, null);

                    logger.println("Switching to the upstream (" + up+")");
                    SVNUpdateClient uc = cm.getUpdateClient();
                    uc.doSwitch(mr, up, HEAD, HEAD, INFINITY, false, true);

                    SVNURL mergeUrl = branchURL != null ? SVNURL.parseURIDecoded(branchURL) : wsState.getURL();
                    SVNRevision mergeRev = branchRev >= 0 ? SVNRevision.create(branchRev) : wsState.getRevision();

                    logger.printf("Merging %s (rev.%s) to the upstream\n",mergeUrl,mergeRev);
                    SVNDiffClient dc = cm.getDiffClient();
                    dc.doMergeReIntegrate(
                            mergeUrl,
                            mergeRev, mr, false);
                    SVNCommitInfo ci=null;
                    if(foundConflict[0]) {
                        logger.println("Found conflict with the upstream. Reverting this failed merge");
                        wc.doRevert(new File[]{mr},INFINITY, null);
                    } else {
                        logger.println("Committing changes to the upstream");
                        SVNCommitClient cc = cm.getCommitClient();
                        ci = cc.doCommit(new File[]{mr}, false, commitMessage, null, null, false, false, INFINITY);
                        if(ci.getNewRevision()<0)
                            logger.println("  No changes since the last integration");
                        else
                            logger.println("  committed revision "+ci.getNewRevision());
                    }

                    logger.println("Switching back to the branch (" + wsState.getURL()+"@"+wsState.getRevision()+")");
                    uc.doSwitch(mr, wsState.getURL(), wsState.getRevision(), wsState.getRevision(), INFINITY, false, true);


                    if (ci!=null && ci.getNewRevision()>=0) {
                        // if the trunk merge produces a commit M, then we want to do "svn merge --record-only -c M <UpstreamURL>"
                        // to convince Subversion not to try to merge rev.M again when re-integrating from the trunk in the future,
                        // equivalent of single commit
                        SVNRevisionRange r = new SVNRevisionRange(SVNRevision.create(ci.getNewRevision()-1),
                                                                  SVNRevision.create(ci.getNewRevision()));
                        String msg = "Block the merge commit rev." + ci.getNewRevision() + " from getting merged back into our branch";
                        logger.println(msg);
                        dc.doMerge(up,HEAD,Arrays.asList(r), mr, INFINITY, true/*opposite of --ignore-ancestory in CLI*/, false, false, true);

                        SVNCommitClient cc = cm.getCommitClient();
                        SVNCommitInfo bci = cc.doCommit(new File[]{mr}, false, msg, null, null, false, false, INFINITY);
                        logger.println("  committed revision "+bci.getNewRevision());
                    }

                    if(foundConflict[0]) {
                        logger.println("Conflict found. Please sync with the upstream to resolve this error.");
                        return -1L;
                    } else {
                        // -1 is returned if there was no commit, so normalize that to 0
                        return Math.max(0,ci.getNewRevision());
                    }
                } catch (SVNException e) {
                    throw new IOException2("Failed to merge", e);
                }
            }
        });
    }

    /**
     * If an upstream is renamed, update the configuration accordingly.
     */
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            if (item instanceof AbstractProject) {
                AbstractProject<?,?> up = (AbstractProject) item;
                if(up.getProperty(IntegratableProject.class)!=null) {
                    try {
                        for (AbstractProject<?,?> p : Jenkins.getInstance().getItems(AbstractProject.class)) {
                            FeatureBranchProperty fbp = p.getProperty(FeatureBranchProperty.class);
                            if(fbp!=null) {
                                if(fbp.upstream.equals(oldName)) {
                                    fbp.upstream=newName;
                                    p.save();
                                }
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to persist configuration",e);
                    }
                }
            }
        }
    }


    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if(!formData.has("svnmerge"))   return null;
            return req.bindJSON(FeatureBranchProperty.class,formData.getJSONObject("svnmerge"));
        }

        public String getDisplayName() {
            return "Upstream Subversion branch";
        }

        public List<AbstractProject<?,?>> listIntegratableProjects() {
            List<AbstractProject<?,?>> r = new ArrayList<AbstractProject<?,?>>();
            for(AbstractProject<?,?> p : Jenkins.getInstance().getItems(AbstractProject.class))
                if(p.getProperty(IntegratableProject.class)!=null)
                    r.add(p);
            return r;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(FeatureBranchProperty.class.getName());
}
