package jenkins.plugins.svnmerge;

/**
 * @author Kohsuke Kawaguchi
 */
public class RebaseSetting {
    /**
     * Revision to rebase with. -1 to rebase to the latest;
     */
    public final long revision;

    public RebaseSetting(long revision) {
        this.revision = revision;
    }
}
