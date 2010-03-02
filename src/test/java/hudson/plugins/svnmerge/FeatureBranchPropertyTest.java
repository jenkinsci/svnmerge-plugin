package hudson.plugins.svnmerge;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.FreeStyleProject;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

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
}
