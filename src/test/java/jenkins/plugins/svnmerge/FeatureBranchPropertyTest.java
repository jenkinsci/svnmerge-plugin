package jenkins.plugins.svnmerge;

import jenkins.model.Jenkins;

import org.apache.commons.collections.iterators.EntrySetMapIterator;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.JobProperty;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.scm.SubversionSCM;
import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;

/**
 * @author Kohsuke Kawaguchi
 */
public class FeatureBranchPropertyTest extends HudsonTestCase {
    public void testConfigRoundtrip1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        HtmlPage page = new WebClient().getPage(p, "configure");
        submit(page.getFormByName("config"));
        assertNull(p.getProperty(FeatureBranchProperty.class));
    }

    public void testConfigRoundtrip2() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.addProperty(new FeatureBranchProperty("xyz"));
        HtmlPage page = new WebClient().getPage(p, "configure");
        submit(page.getFormByName("config"));
        FeatureBranchProperty ujp = p.getProperty(FeatureBranchProperty.class);
        assertNotNull(ujp);
        assertEquals("xyz",ujp.getUpstream());
    }
    
	@Bug(24735)
	public void testUpStreamURLwithParams()
			throws Exception {
		EnvironmentVariablesNodeProperty envNodeProp = new EnvironmentVariablesNodeProperty(new hudson.slaves.EnvironmentVariablesNodeProperty.Entry("ROOT_SVN_URL", "root/"));
		Computer.currentComputer().getNode().getNodeProperties().add(envNodeProp);
		Jenkins.getInstance().getDescriptorList(JobProperty.class).add(new FeatureBranchProperty.DescriptorImpl());
		FreeStyleProject p = createFreeStyleProject();
		p.setScm(new SubversionSCM("https://${ROOT_SVN_URL}${REPO}/${PROJECT}/trunk"));
		ParameterDefinition def1 = new StringParameterDefinition("REPO", "a");
		ParameterDefinition def2 = new StringParameterDefinition("PROJECT", "b");
		p.addProperty(new ParametersDefinitionProperty(def1,def2));
		FeatureBranchProperty jobProp = new FeatureBranchProperty(p.getName());
		FreeStyleProject p2 = createFreeStyleProject();
		p2.addProperty(jobProp);
		for(SVNURL svnURL : jobProp.getUpstreamURL()){
			assertEquals( "https://root/a/b/trunk",svnURL.toDecodedString());
		}
	}
    
    
}
