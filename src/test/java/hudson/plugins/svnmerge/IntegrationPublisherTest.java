package hudson.plugins.svnmerge;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.FreeStyleProject;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Kohsuke Kawaguchi
 */
public class IntegrationPublisherTest extends HudsonTestCase {
    public void testConfigRoundtrip1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        configRoundtrip(p);
        assertNull(p.getPublishersList().get(IntegrationPublisher.class));
    }

    public void testConfigRoundtrip2() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(new IntegrationPublisher());
        configRoundtrip(p);
        assertNotNull(p.getPublishersList().get(IntegrationPublisher.class));
    }

    private void configRoundtrip(FreeStyleProject p) throws Exception {
        HtmlPage page = new WebClient().getPage(p, "configure");
        submit(page.getFormByName("config"));
    }
}
