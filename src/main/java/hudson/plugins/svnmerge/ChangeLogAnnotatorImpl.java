package hudson.plugins.svnmerge;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import hudson.model.Fingerprint;
import hudson.model.Run;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;

/**
 * If a commit from {@link FeatureBranchProperty#integrate(TaskListener, String, long, String)}
 * is found, link back to the build page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ChangeLogAnnotatorImpl extends ChangeLogAnnotator {
    public void annotate(AbstractBuild<?, ?> build, Entry change, MarkupText text) {
        try {
            Fingerprint f = IntegrateAction.getIntegrationFingerprint(change);
            if(f!=null) {
                Run r = f.getOriginal().getRun();
                text.addMarkup(
                    IntegrateAction.COMMIT_MESSAGE_PREFIX.length(),
                    text.length()-IntegrateAction.COMMIT_MESSAGE_SUFFIX.length(),
                    "<a href='"+
                    Stapler.getCurrentRequest().getContextPath()+"/"+r.getUrl()
                    +"'>","</a>");
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
