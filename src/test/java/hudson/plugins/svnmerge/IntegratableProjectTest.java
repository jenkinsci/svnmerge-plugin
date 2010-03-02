package hudson.plugins.svnmerge;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class IntegratableProjectTest extends HudsonTestCase {
    public void testConfigRoundtrip1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        configRoundtrip(p);
        assertNull(p.getProperty(IntegratableProject.class));
    }

    public void testConfigRoundtrip2() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.addProperty(new IntegratableProject());
        configRoundtrip(p);
        assertNotNull(p.getProperty(IntegratableProject.class));
    }

    private void configRoundtrip(FreeStyleProject p) throws Exception {
        HtmlPage page = new WebClient().getPage(p, "configure");
        submit(page.getFormByName("config"));
    }
}
