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

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCopySource;
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
    
    /**
     * 
     * @return
     */
    public RepositoryLayoutInfo getRepositoryLayout() {
    	SCM scm = project.getScm();
        if (!(scm instanceof SubversionSCM)) {
            return null;
        }
        //TODO: check for multiple locations ?
        SubversionSCM svn = (SubversionSCM) scm;
        ModuleLocation firstLocation = svn.getLocations()[0];
        // expand system and node environment variables as well as the project parameters
        firstLocation = Utility.getExpandedLocation(firstLocation, project);
		return getRepositoryLayout(firstLocation);
    }

    private RepositoryLayoutInfo getRepositoryLayout(ModuleLocation location) {
		return new RepositoryLayoutInfo(location.getURL());
    }

    @RequirePOST
    public void doNewBranch(StaplerRequest req, StaplerResponse rsp, 
    						@QueryParameter String name, 
    						@QueryParameter boolean attach, 
    						@QueryParameter String commitMessage, 
    						@QueryParameter String branchLocation,
    						@QueryParameter boolean createTag,
    						@QueryParameter String tagLocation) throws ServletException, IOException {
        
        name = Util.fixEmptyAndTrim(name);
        
        if (name==null) {
        	sendError("Name is required");
        	return;
        }
        
        commitMessage = Util.fixEmptyAndTrim(commitMessage);
        
        if (commitMessage==null) {
        	//the commit message isn't used when attaching to an existing location
        	commitMessage = "Created a feature branch from Jenkins with name: "+name;
        }
        
        SCM scm = project.getScm();
        if (!(scm instanceof SubversionSCM)) {
        	sendError("This project doesn't use Subversion as SCM");
        	return;
        }
        // TODO: check for multiple locations
        SubversionSCM svn = (SubversionSCM) scm;
        ModuleLocation firstLocation = svn.getLocations()[0];
        // expand system and node environment variables as well as the project parameters
        firstLocation = Utility.getExpandedLocation(firstLocation, project);

        RepositoryLayoutInfo layoutInfo = getRepositoryLayout(firstLocation);
        
        branchLocation =  Util.fixEmptyAndTrim(branchLocation);
        tagLocation = Util.fixEmptyAndTrim(tagLocation);
        
        if (layoutInfo.getLayout() == RepositoryLayoutEnum.CUSTOM) {
        	/*
        	 * in case of custom layout the user must provide the full new branch url
        	 * (and optionally the full new tag url)
        	 */
        	if (StringUtils.isEmpty(branchLocation)) {
        		sendError("Branch Location is required for custom repository layout");
        	}
        	if (!attach && createTag && StringUtils.isEmpty(tagLocation)) {
        		sendError("Tag Location is required for custom repository layout");
        	}
        }
        
        String branchUrl;
        if (StringUtils.isNotEmpty(branchLocation)) {
        	//using override value
        	branchUrl = branchLocation;
        } else {
        	//using default value
        	branchUrl = layoutInfo.getDefaultNewBranchUrl().replace("<new_branch_name>", name);
        }
        
        if (!attach) {
        	SvnClientManager svnMgr = SubversionSCM.createClientManager(
        			svn.createAuthenticationProvider(project, firstLocation));
        	
	        List<String> urlsToCopyTo = new ArrayList<String>();
	        SVNURL svnUrl = null;
	    	try {
	    		svnUrl = SVNURL.parseURIEncoded(branchUrl);
	    		SVNInfo info = svnMgr.getWCClient().doInfo(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD);
	    		if(info.getKind()== SVNNodeKind.DIR) {
	    			// ask the user if we should attach
	    			req.getView(this,"_attach.jelly").forward(req,rsp);
	    			return;
	    		} else {
	    			sendError(info.getURL()+" already exists.");
	    			return;
	    		}
	    	} catch (SVNException e) {
	    		// path doesn't exist, the new branch can be created
	    	}
	        urlsToCopyTo.add(branchUrl);
	        
	    	String tagUrl = null;
	        if (createTag) {
	        	//can be true only when not attaching
	        	if (StringUtils.isNotEmpty(tagLocation)) {
	        		//using override value
	        		tagUrl = tagLocation;
	        	} else {
	        		//using default value
	        		tagUrl = layoutInfo.getDefaultNewDevTagUrl().replace("<new_branch_name>", name);
	        	}
	        	try {
	        		svnUrl = SVNURL.parseURIEncoded(tagUrl);
	        		SVNInfo info = svnMgr.getWCClient().doInfo(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD);
	        		sendError(info.getURL()+" already exists.");
	        		return;
	        	} catch (SVNException e) {
	        		// path doesn't exist, the new tag can be created
	        	}
	        	urlsToCopyTo.add(tagUrl);
	        }
        
			if (!createSVNCopy(svnMgr, firstLocation, urlsToCopyTo, commitMessage, req, rsp)) {
				return;
			}
        }
        
    	// if the request wasn't forwarded
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
    						Arrays.asList(firstLocation.withRemote(branchUrl)),
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
    
    /**
     * Utility method for SVN copies creation.
     * First checks if all the given urls already exist; if any exist, creates a copy for each of them.
     * @param scm the project scm
     * @param urlsToCopyTo a list of urls to copy to (i.e. where the copies'll be created).
     * @param commitMessage the commit message to use
     * @param req the original StaplerRequest
     * @param rsp the original StaplerResponse
     * @throws ServletException
     * @throws IOException
     */
    private boolean createSVNCopy(SvnClientManager svnMgr, ModuleLocation originalLocation, List<String> urlsToCopyTo, 
    						   String commitMessage, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
         
        try {
            for (String urlToCopyTo : urlsToCopyTo) {
            	SVNURL dst = SVNURL.parseURIEncoded(urlToCopyTo);
            	svnMgr.getCopyClient().doCopy(
                        new SVNCopySource[] {
                                new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, originalLocation.getSVNURL()) },
                        dst, 
            		false, 
            		true,
            		true,
            		commitMessage,
                        new SVNProperties());
            }
            
            return true;
        } catch (SVNException e) {
            sendError(e);
        	return false;
        }
	}

}
