package jenkins.plugins.svnmerge;

import hudson.BulkChange;
import hudson.Util;
import hudson.model.Action;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.scm.SvnClientManager;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Project-level {@link Action} that shows the feature branches.
 *
 * <p>
 * This is attached to the upstream job.
 *
 * @author Kohsuke Kawaguchi
 */
public class IntegratableProjectAction extends AbstractModelObject implements Action {
    public final AbstractProject<?,?> project;

    private final IntegratableProject ip;

    /*package*/ IntegratableProjectAction(IntegratableProject ip) {
        this.ip = ip;
        this.project = ip.getOwner();
    }

    public String getIconFileName() {
        return "/plugin/svnmerge/24x24/sync.gif";
    }

    public String getDisplayName() {
        return "Feature Branches";
    }

    public String getSearchUrl() {
        return getDisplayName();
    }

    public String getUrlName() {
        return "featureBranches";
    }

    /**
     * Gets feature branches for this project.
     */
    public List<AbstractProject<?,?>> getBranches() {
        String n = project.getName();
        List<AbstractProject<?,?>> r  = new ArrayList<AbstractProject<?,?>>();
        for (AbstractProject<?,?> p : Jenkins.getInstance().getItems(AbstractProject.class)) {
            FeatureBranchProperty fbp = p.getProperty(FeatureBranchProperty.class);
            if(fbp!=null && fbp.getUpstream().equals(n))
                r.add(p);
        }
        return r;
    }

    public void doNewBranch(StaplerRequest req, StaplerResponse rsp, @QueryParameter String name, @QueryParameter boolean attach, @QueryParameter String commitMessage) throws ServletException, IOException {
        requirePOST();

        name = Util.fixEmptyAndTrim(name);
        
        if (name==null) {
            sendError("Name is required");
            return;
        }

        commitMessage = Util.fixEmptyAndTrim(commitMessage);

        if (commitMessage==null) {
            commitMessage = "Created a feature branch from Jenkins";
        }
        
        SCM scm = project.getScm();
        if (!(scm instanceof SubversionSCM)) {
            sendError("This project doesn't use Subversion as SCM");
            return;
        }

        // TODO: check for multiple locations
        SubversionSCM svn = (SubversionSCM) scm;
        ModuleLocation firstLocation = svn.getLocations()[0];
		String url = firstLocation.getURL();
        Matcher m = KEYWORD.matcher(url);
        if(!m.find()) {
            sendError("Unable to infer the new branch name from "+url);
            return;
        }
        url = url.substring(0,m.start())+"/branches/"+name;

        if(!attach) {
            SvnClientManager svnm = SubversionSCM.createClientManager(
            		svn.createAuthenticationProvider(project, firstLocation));
            try {
                SVNURL dst = SVNURL.parseURIEncoded(url);

                // check if the branch already exists
                try {
                    SVNInfo info = svnm.getWCClient().doInfo(dst, SVNRevision.HEAD, SVNRevision.HEAD);
                    if(info.getKind()== SVNNodeKind.DIR) {
                        // ask the user if we should attach
                        req.getView(this,"_attach.jelly").forward(req,rsp);
                        return;
                    } else {
                        sendError(info.getURL()+" already exists.");
                        return;
                    }
                } catch (SVNException e) {
                    // path doesn't exist, which is good
                }

                // create a branch
                svnm.getCopyClient().doCopy(
                    firstLocation.getSVNURL(), SVNRevision.HEAD,
                    dst, false, true,
                    commitMessage);
            } catch (SVNException e) {
                sendError(e);
                return;
            }
        }

        // copy a job, and adjust its properties for integration
        AbstractProject<?,?> copy = Jenkins.getInstance().copy(project, project.getName() + "-" + name.replaceAll("/", "-"));
        BulkChange bc = new BulkChange(copy);
        try {
            copy.removeProperty(IntegratableProject.class);
            ((AbstractProject)copy).addProperty(new FeatureBranchProperty(project.getName())); // pointless cast for working around javac bug as of JDK1.6.0_02
            // update the SCM config to point to the branch
            SubversionSCM svnScm = (SubversionSCM)copy.getScm();
            copy.setScm(
                    new SubversionSCM(
                            Arrays.asList(firstLocation.withRemote(url)),
                                svnScm.getWorkspaceUpdater(),
                                svnScm.getBrowser(),
                                svnScm.getExcludedRegions(),
                                svnScm.getExcludedUsers(),
                                svnScm.getExcludedRevprop(),
                                svnScm.getExcludedCommitMessages(),
                                svnScm.getIncludedRegions(),
                                svnScm.isIgnoreDirPropChanges(),
                                svnScm.isFilterChangelog(),
                                svnScm.getAdditionalCredentials()
                            ));
        } finally {
            bc.commit();
        }

        rsp.sendRedirect2(req.getContextPath()+"/"+copy.getUrl());
    }

    private static final Pattern KEYWORD = Pattern.compile("/(trunk(/|$)|branches/)");
}
