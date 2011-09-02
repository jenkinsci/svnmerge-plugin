package jenkins.plugins.svnmerge;

import hudson.model.PermalinkProjectAction.Permalink;

/**
 * @author Kohsuke Kawaguchi
 */
public class RebaseSetting {
    /**
     * Revision to rebase with. -1 to rebase to the latest;
     */
    public final long revision;

    /**
     * Permalink ID of the upstream to rebase to.
     * If this value is non-null, it takes precedence over {@link #revision}
     */
    public final String permalink;

    public RebaseSetting(long revision) {
        this.revision = revision;
        this.permalink = null;
    }

    public RebaseSetting(String permalink) {
        this.revision = -1;
        this.permalink = permalink;
    }

    public RebaseSetting(Permalink p) {
        this(p.getId());
    }
}
