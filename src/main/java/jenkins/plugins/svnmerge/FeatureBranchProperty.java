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
import hudson.scm.SvnClientManager;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.util.IOException2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
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
import java.io.Serializable;
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
public class FeatureBranchProperty extends JobProperty<AbstractProject<?,?>> implements Serializable {
    private static final long serialVersionUID = -1L; 
    
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
     *      Revision of the upstream to rebase from.
     *      If -1, use the latest.
     * @return
     *      the new revision number if the rebase was successful.
     *      -1 if it failed and the failure was handled gracefully
     *      (typically this means a merge conflict.)
     */
    public long rebase(final TaskListener listener, final long upstreamRev) throws IOException, InterruptedException {
        final SubversionSCM svn = (SubversionSCM) getOwner().getScm();
        final ISVNAuthenticationProvider provider = svn.createAuthenticationProvider(getOwner(), svn.getLocations()[0]);

        final ModuleLocation upstreamLocation = getUpstreamSubversionLocation();
        
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

                    SvnClientManager svnm = SubversionSCM.createClientManager(provider);

					SVNURL up = upstreamLocation == null ? null : upstreamLocation.getSVNURL();
                    SVNClientManager cm = svnm.getCore();
                    cm.setEventHandler(printHandler);

                    SVNWCClient wc = cm.getWCClient();
                    SVNDiffClient dc = cm.getDiffClient();

                    logger.printf("Updating workspace to the latest revision\n");
                    long wsr = cm.getUpdateClient().doUpdate(mr, HEAD, INFINITY, false, false);
//                    logger.printf("  Updated to rev.%d\n",wsr);  // reported by printHandler

                    SVNRevision mergeRev = upstreamRev >= 0 ? SVNRevision.create(upstreamRev) : cm.getWCClient().doInfo(up,HEAD,HEAD).getCommittedRevision();

                    logger.printf("Merging change from the upstream %s at rev.%s\n",up,mergeRev);
                    SVNRevisionRange r = new SVNRevisionRange(SVNRevision.create(0),mergeRev);
                    dc.doMerge(up, mergeRev, Arrays.asList(r), mr, INFINITY, true, false, false, false);
                    if(foundConflict[0]) {
                        logger.println("Found conflict. Reverting this failed merge");
                        wc.doRevert(new File[]{mr},INFINITY, null);
                        return -1L;
                    } else {
                        logger.println("Committing changes");
                        SVNCommitClient cc = cm.getCommitClient();
                        SVNCommitInfo ci = cc.doCommit(new File[]{mr}, false, RebaseAction.COMMIT_MESSAGE_PREFIX+"Rebasing from "+up+"@"+mergeRev, null, null, false, false, INFINITY);
                        if(ci.getNewRevision()<0) {
                            logger.println("  No changes since the last rebase. This rebase was a no-op.");
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
     * Represents the result of integration.
     */
    public static class IntegrationResult implements Serializable {
        private static final long serialVersionUID = -1L; 

        /**
         * The merge commit in the upstream where the integration is made visible to the upstream.
         * Or 0 if the integration was no-op and no commit was made.
         * -1 if it failed and the failure was handled gracefully
         * (typically this means a merge conflict.)
         */
        public final long mergeCommit;

        /**
         * The commit in the branch that was merged (or attempted to be merged.)
         */
        public final long integrationSource;

        public IntegrationResult(long mergeCommit, SVNRevision integrationSource) {
            this.mergeCommit = mergeCommit;
            this.integrationSource = integrationSource.getNumber();
            assert this.integrationSource!=-1L;
        }
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
     *      Always non-null. See {@link IntegrationResult}
     */
    public IntegrationResult integrate(final TaskListener listener, final String branchURL, final long branchRev, final String commitMessage) throws IOException, InterruptedException {
        final Long lastIntegrationSourceRevision = getlastIntegrationSourceRevision();

        final SubversionSCM svn = (SubversionSCM) getUpstreamProject().getScm();
        final ISVNAuthenticationProvider provider = svn.createAuthenticationProvider(getUpstreamProject(), svn.getLocations()[0]);

        final ModuleLocation upstreamLocation = getUpstreamSubversionLocation();
        
        return owner.getModuleRoot().act(new FileCallable<IntegrationResult>() {
            public IntegrationResult invoke(File mr, VirtualChannel virtualChannel) throws IOException {
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

                    SvnClientManager svnm = SubversionSCM.createClientManager(provider);

					SVNURL up = upstreamLocation == null ? null : upstreamLocation.getSVNURL();
                    SVNClientManager cm = svnm.getCore();
                    cm.setEventHandler(printHandler);

                    // capture the working directory state before the switch
                    SVNWCClient wc = cm.getWCClient();
                    SVNInfo wsState = wc.doInfo(mr, null);
                    SVNURL mergeUrl = branchURL != null ? SVNURL.parseURIDecoded(branchURL) : wsState.getURL();
                    SVNRevision mergeRev = branchRev >= 0 ? SVNRevision.create(branchRev) : wsState.getRevision();

                    // do we have any meaningful changes in this branch worthy of integration?
                    if (lastIntegrationSourceRevision !=null) {
                        final SVNException eureka = new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
                        try {
                            cm.getLogClient().doLog(new File[]{mr},mergeRev,SVNRevision.create(lastIntegrationSourceRevision),mergeRev,true,false,-1,new ISVNLogEntryHandler() {
                                public void handleLogEntry(SVNLogEntry e) throws SVNException {
                                    if (e.getMessage().startsWith(RebaseAction.COMMIT_MESSAGE_PREFIX)
                                     || e.getMessage().startsWith(IntegrateAction.COMMIT_MESSAGE_PREFIX))
                                        return; // merge commits
                                    throw eureka;
                                }
                            });
                            // didn't find anything interesting. all the changes are our merges
                            logger.println("No changes to be integrated. Skipping integration.");
                            return new IntegrationResult(0,mergeRev);
                        } catch (SVNException e) {
                            if (e!=eureka)
                                throw e;    // some other problems
                            // found some changes, keep on integrating
                        }
                    }
                    
                    logger.println("Switching to the upstream (" + up+")");
                    SVNUpdateClient uc = cm.getUpdateClient();
                    uc.doSwitch(mr, up, HEAD, HEAD, INFINITY, false, false);

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
                        ci = cc.doCommit(new File[]{mr}, false, commitMessage+"\n"+mergeUrl+"@"+mergeRev, null, null, false, false, INFINITY);
                        if(ci.getNewRevision()<0)
                            logger.println("  No changes since the last integration");
                        else
                            logger.println("  committed revision "+ci.getNewRevision());
                    }

                    logger.println("Switching back to the branch (" + wsState.getURL()+"@"+wsState.getRevision()+")");
                    uc.doSwitch(mr, wsState.getURL(), wsState.getRevision(), wsState.getRevision(), INFINITY, false, true);

                    if(foundConflict[0]) {
                        logger.println("Conflict found. Please sync with the upstream to resolve this error.");
                        return new IntegrationResult(-1,mergeRev);
                    }

                    long trunkCommit = ci.getNewRevision();

                    if (trunkCommit>=0) {
                        cm.getUpdateClient().doUpdate(mr, HEAD, INFINITY, false, false);
                        SVNCommitClient cc = cm.getCommitClient();

                        if (false) {
                            // taking Jack Repennings advise not to do this.

                            // if the trunk merge produces a commit M, then we want to do "svn merge --record-only -c M <UpstreamURL>"
                            // to convince Subversion not to try to merge rev.M again when re-integrating from the trunk in the future,
                            // equivalent of single commit
                            SVNRevisionRange r = new SVNRevisionRange(SVNRevision.create(trunkCommit-1),
                                                                      SVNRevision.create(trunkCommit));
                            String msg = "Block the merge commit rev." + trunkCommit + " from getting merged back into our branch";
                            logger.println(msg);
                            dc.doMerge(up,HEAD,Arrays.asList(r), mr, INFINITY, true/*opposite of --ignore-ancestory in CLI*/, false, false, true);

                            SVNCommitInfo bci = cc.doCommit(new File[]{mr}, false, msg, null, null, false, false, INFINITY);
                            logger.println("  committed revision "+bci.getNewRevision());
                            cm.getUpdateClient().doUpdate(mr, HEAD, INFINITY, false, false);
                        }

                        // this is the black magic part, but my experiments reveal that we need to run trunk->branch merge --reintegrate
                        // or else future rebase fails
                        logger.printf("Merging change from the upstream %s at rev.%s\n",up,trunkCommit);
                        dc.doMergeReIntegrate(up, SVNRevision.create(trunkCommit), mr, false);
                        if(foundConflict[0]) {
                            uc.doSwitch(mr, wsState.getURL(), wsState.getRevision(), wsState.getRevision(), INFINITY, false, true);
                            logger.println("Conflict found. Please sync with the upstream to resolve this error.");
                            return new IntegrationResult(-1,mergeRev);
                        }

                        String msg = RebaseAction.COMMIT_MESSAGE_PREFIX+"Rebasing with the integration commit that was just made in rev."+trunkCommit;
                        SVNCommitInfo bci = cc.doCommit(new File[]{mr}, false, msg, null, null, false, false, INFINITY);
                        logger.println("  committed revision "+bci.getNewRevision());
                    }

                    // -1 is returned if there was no commit, so normalize that to 0
                    return new IntegrationResult(Math.max(0,trunkCommit),mergeRev);
                } catch (SVNException e) {
                    throw new IOException2("Failed to merge", e);
                }
            }
        });
    }

    private Long getlastIntegrationSourceRevision() {
        IntegrateAction ia = IntegrationStatusAction.getLastIntegrateAction(owner);
        if (ia!=null)   return ia.getIntegrationSource();
        return null;
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
