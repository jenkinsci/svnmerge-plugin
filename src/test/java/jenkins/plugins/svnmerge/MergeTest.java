package jenkins.plugins.svnmerge;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SubversionSCM;
import hudson.util.IOException2;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;
import org.tmatesoft.svn.core.SVNCommitInfo;
import static org.tmatesoft.svn.core.SVNDepth.INFINITY;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import static org.tmatesoft.svn.core.wc.SVNRevision.HEAD;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.awt.*;

import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlForm;

/**
 * @author Kohsuke Kawaguchi
 */
public class MergeTest extends HudsonTestCase {
    private URL repo;
    private FreeStyleProject trunk;
    private FreeStyleProject p;
    private FeatureBranchProperty upp;
    private File ws;
    private SVNClientManager cm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repo = loadSvn();

        // create the trunk project
        trunk = createFreeStyleProject("trunk");
        trunk.setScm(new SubversionSCM("file://"+new URL(repo,"trunk").getPath(),"trunk"));
        trunk.addProperty(new IntegratableProject());

        // create a project
        p = createFreeStyleProject("b1");
        p.setScm(new SubversionSCM("file://"+new URL(repo,"branches/b1").getPath(),"b1"));
        upp = new FeatureBranchProperty(trunk.getName());
        p.addProperty(upp);

        // check out workspace so that we can simulate commits outside Jenkins
        ws = env.temporaryDirectoryAllocator.allocate();
        cm = SubversionSCM.createSvnClientManager(trunk);
        cm.getUpdateClient().doCheckout(SVNURL.parseURIDecoded("file://"+repo.getPath()), ws, null, HEAD, INFINITY, true);
    }

    /**
     * Create a new feature branch
     */
    public void testCreateBranch() throws Exception {
        createFeatureBranch(new WebClient(), "b2");

        // make sure it's created with the right set of linkage to the trunk job
        FreeStyleProject fsp = hudson.getItemByFullName("trunk-b2",FreeStyleProject.class);
        assertNotNull(fsp);
        assertNull(fsp.getProperty(IntegratableProject.class));
        FeatureBranchProperty ujp = fsp.getProperty(FeatureBranchProperty.class);
        assertNotNull(ujp);
        assertSame(ujp.getUpstreamProject(),trunk);
        assertTrue(((SubversionSCM)fsp.getScm()).getLocations()[0].getURL().contains("/branches/b2"));

        // see if the rename works
        trunk.renameTo("somethingElse");
        assertSame(ujp.getUpstreamProject(),trunk);
        assertEquals(trunk.getName(),ujp.getUpstream());

        // try recreating the branch job and make sure Hudson detects an error
        fsp.delete();
        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = createFeatureBranch(wc, "b2");
        assertEquals("Duplicate branch should be detected",400,p.getWebResponse().getStatusCode());
        submit(p.getFormByName("new"));
    }

    private HtmlPage createFeatureBranch(WebClient webClient, String name) throws Exception {
        HtmlPage p = webClient.getPage(trunk, "featureBranches");
        HtmlForm f = p.getFormByName("new");
        f.getInputByName("name").setValue(name);
        return submit(f);
    }

    /**
     * Very basic test that pushes changes to the upstream.
     */
    public void testUpstreamMerge() throws Exception {
        commitAndUpdate("branches/b1/e");   // make a non-conflicting change in the branch

        p.addPublisher(new IntegrationPublisher());
        assertBuildStatusSuccess(build());

        assertTrue(trunkHasE());
    }

    /**
     * Verify that there's trunk/e, which is created by {@link #nonCollidingChange}.
     */
    private boolean trunkHasE() throws SVNException {
        // make sure the merge went in by checking if /trunk/e exists.
        SVNRepository rep = SVNRepositoryFactory.create(upp.getUpstreamURL());
        long latest = rep.getLatestRevision();
        try {
            return latest==rep.getFile("/trunk/e", latest,new SVNProperties(), null);
        } catch (SVNException e) {
            return false;
        }
    }

    /**
     * Now what if a merge fails with a conflict?
     */
    public void testUpstreamConflict() throws Exception {
        commitAndUpdate("branches/b1/d");   // make a conflicting change in the branch

        p.addPublisher(new IntegrationPublisher());

        // merge should have failed, because of a conflict
        FreeStyleBuild build = build();
        assertBuildStatus(Result.FAILURE, build);
        assertLogContains("Found conflict with the upstream",build);

        // workspace should have our d.
        assertEquals(MAGIC_CONTENT,IOUtils.toString(p.getModuleRoot().child("d").read()));
    }

    /**
     * Tests manual integration.
     */
    public void testManualIntegration() throws Exception {
        FreeStyleBuild b = assertBuildStatusSuccess(build());

        commitAndUpdate("branches/b1/e");   // make a non-conflicting change in the branch

        // make a change in the branch, but don't auto-commit the change
        integrateManually(b);

        // this should merge the branch as it was checked out, so it should succeed
        // but E won't be integrated yet.
        assertFalse(trunkHasE());

        p.getBuildersList().clear();
        b = assertBuildStatusSuccess(build());
        integrateManually(b);
        assertTrue(trunkHasE());
    }

    private String integrateManually(FreeStyleBuild b) throws InterruptedException, ExecutionException, IOException {
        System.out.println("-- Now Merging manually");
        IntegrateAction ma = b.getAction(IntegrateAction.class);
        // XXX this can block indefinitely!
        IntegrateAction.WorkerThread thread = ma.performAsync(new IntegrateSetting()).get();
        String msg = IOUtils.toString(thread.readAll());
        System.out.println(msg);
        return msg;
    }

    /**
     * Manual integration should also fail if there's a collision
     */
    public void testManualIntegrationCollision() throws Exception {
        FreeStyleBuild b = assertBuildStatusSuccess(build());

        commitAndUpdate("branches/b1/d");   // make a conflicting change in the branch

        String msg = integrateManually(b);
        // this should succeed because we are integrating a commit before above
        // assertFalse(msg.contains("Conflict found"));

        // incorret --- it fails because rebase after integrate will fail with conflict
        assertTrue(msg.contains("Conflict found"));
    }

    /**
     * Feature branch should be able to receive changes from the trunk as well as pushing them back into the main in arbitrary numbers and orders.
     * Make sure it works
     */
    public void testReflectiveMerge() throws Exception {
        commitAndUpdate("trunk/step1");
        commitAndUpdate("branches/b1/step2");

        // rebase
//        cm.getDiffClient().doMergeReIntegrate();

        // push changes up
        p.getPublishersList().add(new IntegrationPublisher());
        assertBuildStatusSuccess(build());
    }

    private void commitAndUpdate(String path) throws SVNException, IOException {
        File f = new File(ws, path);
        FileUtils.writeStringToFile(f,MAGIC_CONTENT);
        cm.getWCClient().doAdd(f,false,false,false, INFINITY,false,false);
        cm.getCommitClient().doCommit(new File[]{f}, false, "edit", null, null, false, false, INFINITY);
        cm.getUpdateClient().doUpdate(ws, HEAD, INFINITY, false, true);
    }


    /**
     * Add a file and then commit the directory.
     */
    private void commitAndUpdate(BuildListener listener, FilePath dir, FilePath newFile) throws IOException2 {
        try {
            SVNClientManager cm = SubversionSCM.createSvnClientManager(p);

            cm.getWCClient().doAdd(toFile(newFile),false,false,false, INFINITY,false,false);
            SVNCommitInfo ci = cm.getCommitClient().doCommit(new File[]{toFile(dir)}, false, "a change in a branch", null, null, false, false, INFINITY);
            listener.getLogger().println("Committed "+newFile+" at "+ci.getNewRevision());

            cm.getUpdateClient().doUpdate(new File(dir.getRemote()), HEAD, INFINITY, false, true);
        } catch (SVNException x) {
            throw new IOException2("failed to commit",x);
        }
    }

    private File toFile(FilePath fp) {
        return new File(fp.getRemote());
    }

    private FilePath touch(FilePath mr, String name) throws IOException, InterruptedException {
        FilePath d = mr.child(name);
        d.write(MAGIC_CONTENT,"UTF-8");
        return d;
    }

    /**
     * Lets the build run and merge the change.
     */
    private FreeStyleBuild build() throws Exception {
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        return b;
    }

    private URL loadSvn() throws Exception {
        /*  Contents of this repository
------------------------------------------------------------------------
r4 | kohsuke | 2009-01-16 16:15:32 -0800 (Fri, 16 Jan 2009) | 1 line
Changed paths:
   A /trunk/d

trunk is moving ahead after branched b1
------------------------------------------------------------------------
r3 | kohsuke | 2009-01-16 16:15:12 -0800 (Fri, 16 Jan 2009) | 2 lines
Changed paths:
   A /branches/b1 (from /trunk:1)
   A /branches/b1/a (from /trunk/a:2)
   A /branches/b1/b (from /trunk/b:2)
   A /branches/b1/c (from /trunk/c:2)

Branched

------------------------------------------------------------------------
r2 | kohsuke | 2009-01-16 16:14:42 -0800 (Fri, 16 Jan 2009) | 1 line
Changed paths:
   A /trunk/a
   A /trunk/b
   A /trunk/c

trunk version
------------------------------------------------------------------------
r1 | kohsuke | 2009-01-16 16:14:17 -0800 (Fri, 16 Jan 2009) | 1 line
Changed paths:
   A /branches
   A /trunk

created a structure
------------------------------------------------------------------------

         */
        return new CopyExisting(getClass().getResource("repo.zip")).allocate().toURI().toURL();
    }

    private static final String MAGIC_CONTENT = "created in branch";
}
