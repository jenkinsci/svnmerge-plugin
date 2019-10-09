package jenkins.plugins.svnmerge;

import hudson.BulkChange;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.AbstractItem;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.scm.SvnClientManager;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.security.AccessControlled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.DirectlyModifiableTopLevelItemGroup;
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
        // looking for the project's full name because we want to search nested projects
    	String n = project.getFullName();
        List<AbstractProject<?,?>> r  = new ArrayList<AbstractProject<?,?>>();
        // iterating over all items in tree (recursively)
        for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
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
    public ArrayList<RepositoryLayoutInfo> getRepositoryLayout() {
		ArrayList<RepositoryLayoutInfo> toReturn = new ArrayList<RepositoryLayoutInfo>();
    	SCM scm = project.getScm();
        if (!(scm instanceof SubversionSCM)) {
            return null;
        }

        SubversionSCM svn = (SubversionSCM) scm;
		for(ModuleLocation ml : svn.getLocations()){
			// expand system and node environment variables as well as the project parameters
			ml = Utility.getExpandedLocation(ml, project);
			toReturn.add(getRepositoryLayout(ml));
		}

		return toReturn;
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

        SubversionSCM svn = (SubversionSCM) scm;

		//Create list with all new locations. This will be later copied to the new job
		List<ModuleLocation> locationsToCopy = new ArrayList<ModuleLocation>();

		for(ModuleLocation currentLocation : svn.getLocations()){
			// expand system and node environment variables as well as the project parameters
			currentLocation = Utility.getExpandedLocation(currentLocation, project);

			RepositoryLayoutInfo layoutInfo = getRepositoryLayout(currentLocation);

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
						svn.createAuthenticationProvider(project, currentLocation));

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

				if (!createSVNCopy(svnMgr, currentLocation, urlsToCopyTo, commitMessage, req, rsp)) {
					return;
				}
			}
			//Add new location, we will copy in new job's Subversion
			locationsToCopy.add(currentLocation.withRemote(branchUrl));
		}

		// if the request wasn't forwarded
		// copy a job, and adjust its properties for integration
		AbstractProject<?,?> copy = Jenkins.getInstance().copy(project, project.getName() + "-" + name.replaceAll("/", "-"));
		BulkChange bc = new BulkChange(copy);

		try {

			// moving the copy to its parent location
			moveProjectToItsUpstreamProjectLocation(copy, project);
			// the copy doesn't born accepting integration from subversion feature branches..
			copy.removeProperty(IntegratableProject.class);
			// ... and it's a feature branch of its upstream project (which can
			// be located anywhere in the tree, that's why we are pointing
			// to its full name)
			((AbstractProject)copy).addProperty(new FeatureBranchProperty(project.getFullName())); // pointless cast for working around javac bug as of JDK1.6.0_02
			// update the SCM config to point to the branch
			SubversionSCM svnScm = (SubversionSCM)copy.getScm();


			copy.setScm(
					new SubversionSCM(
							locationsToCopy,
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
     * Utility method to move a project to the same location of its upstream project.
     *
     * @param project
     * @param upstreamProject
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
	private <I extends AbstractItem & TopLevelItem> void moveProjectToItsUpstreamProjectLocation(
			AbstractProject<?, ?> project, AbstractProject<?, ?> upstreamProject)
			throws IOException {

    	// we need to check if the upstream project isn't in the root (hudson.model.Hudson)
		if (upstreamProject.getParent() != null
				&& (upstreamProject.getParent() instanceof DirectlyModifiableTopLevelItemGroup)
				&& !(upstreamProject.getParent() instanceof Jenkins)) {

			// get the right destination
			DirectlyModifiableTopLevelItemGroup destination = (DirectlyModifiableTopLevelItemGroup) upstreamProject
					.getParent();
            // check if we can move to this destination
			if (!(destination == project.getParent() || destination
					.canAdd((TopLevelItem) project)
					&& ((AccessControlled) destination)
							.hasPermission(Job.CREATE))) {
                return;
            }
			// moving
            Items.move((I)project, destination);
        }
    }

    /**
     * Utility method for SVN copies creation.
     * First checks if all the given urls already exist; if any exist, creates a copy for each of them.
     * @param svnMgr the project scm
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
