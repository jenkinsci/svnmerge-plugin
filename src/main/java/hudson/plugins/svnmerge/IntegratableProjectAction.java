package hudson.plugins.svnmerge;

import hudson.BulkChange;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        for (AbstractProject<?,?> p : Hudson.getInstance().getItems(AbstractProject.class)) {
            FeatureBranchProperty fbp = p.getProperty(FeatureBranchProperty.class);
            if(fbp!=null && fbp.getUpstream().equals(n))
                r.add(p);
        }
        return r;
    }

    public void doNewBranch(StaplerRequest req, StaplerResponse rsp, @QueryParameter String name, @QueryParameter boolean attach) throws ServletException, IOException {
        requirePOST();

        name = Util.fixEmptyAndTrim(name);

        if (name==null) {
            sendError("Name is required");
            return;
        }

        SCM scm = project.getScm();
        if (!(scm instanceof SubversionSCM)) {
            sendError("This project doesn't use Subversion as SCM");
            return;
        }

        // TODO: check for multiple locations
        SubversionSCM svn = (SubversionSCM) scm;
        String url = svn.getLocations()[0].getURL();
        Matcher m = KEYWORD.matcher(url);
        if(!m.find()) {
            sendError("Unable to infer the new branch name from "+url);
            return;
        }
        url = url.substring(0,m.start())+"/branches/"+name;

        if(!attach) {
            SVNClientManager svnm = SubversionSCM.createSvnClientManager();
            try {
                SVNURL dst = SVNURL.parseURIEncoded(url);

                // check if the branch already exists
                try {
                    SVNInfo info = svnm.getWCClient().doInfo(dst, null, null);
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
                    svn.getLocations()[0].getSVNURL(), SVNRevision.HEAD,
                    dst, false, true,
                    "Created a feature branch from Hudson");
            } catch (SVNException e) {
                sendError(e);
                return;
            }
        }

        // copy a job, and adjust its properties for integration
        AbstractProject<?,?> copy = Hudson.getInstance().copy(project, project.getName() + "-" + name);
        BulkChange bc = new BulkChange(copy);
        try {
            copy.removeProperty(IntegratableProject.class);
            ((AbstractProject)copy).addProperty(new FeatureBranchProperty(project.getName())); // pointless cast for working around javac bug as of JDK1.6.0_02
            // update the SCM config to point to the branch
            SubversionSCM svnScm = (SubversionSCM)copy.getScm();
            copy.setScm(new SubversionSCM(new String[]{url},new String[]{null},svnScm.isUseUpdate(),svnScm.getBrowser()));
        } finally {
            bc.commit();
        }

        rsp.sendRedirect2(req.getContextPath()+"/"+copy.getUrl());
    }

    private static final Pattern KEYWORD = Pattern.compile("/(trunk(/|$)|branches/)");
}
