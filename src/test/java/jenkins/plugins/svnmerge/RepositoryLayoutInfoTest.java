package jenkins.plugins.svnmerge;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test class for {@link RepositoryLayoutInfo}
 * @author Verny Quartara Gutierrez
 *
 */
public class RepositoryLayoutInfoTest {

	@Test
	public void testConstructor() {
		RepositoryLayoutInfo repoLayout;
		String scmModuleLocation = "http://svnserver:port/trunk";
		
		//single project layout, starting from trunk
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//single project layout, starting from trunk
		scmModuleLocation = "http://svnserver:port/trunk/";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals("http://svnserver:port/trunk", repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//single project layout, starting from a branch
		scmModuleLocation = "http://svnserver:port/branches/brid";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//single project layout, starting from a branch
		scmModuleLocation = "http://svnserver:port/branches/brid/";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals("http://svnserver:port/branches/brid", repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi non-related project layout, starting from trunk
		scmModuleLocation = "http://svnserver:port/project/trunk";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/project/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/project/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi non-related project layout, starting from a branch
		scmModuleLocation = "http://svnserver:port/project/branches/brid";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.SINGLE, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/project/branches/<new_branch_name>/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/project/tags/dev/<new_branch_name>/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from trunk
		scmModuleLocation = "http://svnserver:port/trunk/project";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNotNull(repoLayout.getSubProjectName());
		assertEquals("project", repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/project/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/project/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from a branch
		scmModuleLocation = "http://svnserver:port/branches/brid/project";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNotNull(repoLayout.getSubProjectName());
		assertEquals("project", repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/branches/<new_branch_name>/project/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/tags/dev/<new_branch_name>/project/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from trunk
		scmModuleLocation = "http://svnserver:port/name/trunk/project";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNotNull(repoLayout.getSubProjectName());
		assertEquals("project", repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/name/branches/<new_branch_name>/project/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/name/tags/dev/<new_branch_name>/project/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from a branch
		scmModuleLocation = "http://svnserver:port/name/branches/brid/project";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNotNull(repoLayout.getSubProjectName());
		assertEquals("project", repoLayout.getSubProjectName());
		assertEquals("http://svnserver:port/name/branches/<new_branch_name>/project/", repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://svnserver:port/name/tags/dev/<new_branch_name>/project/", repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from trunk (dash character)
		scmModuleLocation = "http://192.168.1.62:8081/svn/multiprojectlayout/trunk/test-prj";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertEquals("test-prj", repoLayout.getSubProjectName());
		assertEquals("http://192.168.1.62:8081/svn/multiprojectlayout/branches/<new_branch_name>/test-prj/", 
				     repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://192.168.1.62:8081/svn/multiprojectlayout/tags/dev/<new_branch_name>/test-prj/", 
					 repoLayout.getDefaultNewDevTagUrl());
		
		//multi closely-related project layout, starting from trunk (dash character)
		scmModuleLocation = "http://192.168.1.62:8081/svn/multiprojectlayout/branches/brid/test_prj";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.MULTI, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertEquals("test_prj", repoLayout.getSubProjectName());
		assertEquals("http://192.168.1.62:8081/svn/multiprojectlayout/branches/<new_branch_name>/test_prj/", 
				repoLayout.getDefaultNewBranchUrl());
		assertEquals("http://192.168.1.62:8081/svn/multiprojectlayout/tags/dev/<new_branch_name>/test_prj/", 
				repoLayout.getDefaultNewDevTagUrl());
		
		//custom layout
		scmModuleLocation = "http://svnserver:port/trunk/A/B";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		//custom layout
		scmModuleLocation = "http://svnserver:port/branches/brid/A/B";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		//custom layout
		scmModuleLocation = "http://svnserver:port/project/trunk/A/B";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		//custom layout
		scmModuleLocation = "http://svnserver:port/project/branches/brid/A/B";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		//custom layout
		scmModuleLocation = "http://svnserver.com/FolderTrunk/Folder2/Folder3/Folder4/ProjectName";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
		//custom layout
		scmModuleLocation = "http://svnserver.com/FolderBranches/Folder2/A/B/C/FeatureBranch";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());

		//custom layout
		scmModuleLocation = "svn://localhost/repo/trunk/project/subproject";
		repoLayout = new RepositoryLayoutInfo(scmModuleLocation);
		assertEquals(RepositoryLayoutEnum.CUSTOM, repoLayout.getLayout());
		assertEquals(scmModuleLocation, repoLayout.getScmModuleLocation());
		assertNull(repoLayout.getSubProjectName());
	}
}
