//   Show the last integration status
package jenkins.plugins.svnmerge.IntegrationStatusAction

import lib.LayoutTagLib
import lib.JenkinsTagLib
import hudson.Functions

def l = namespace(LayoutTagLib.class)
def t = namespace(JenkinsTagLib.class)

l.layout(norefresh:true, title:_("title",my.project.displayName)) {
    include(my.project, "sidepanel")
    l.main_panel {
        h1 {
            img(src:"${rootURL}/plugin/svnmerge/48x48/integrate.gif")
            text(_("title",my.project.displayName))
        }

        def ia = my.lastIntegrateAction;

        if (ia==null) {
            p("""
            This project has not been integrated to
            <a href="${Functions.getRelativeLinkTo(my.branchProperty.upstreamProject)}">the upstream</a> yet."))
            """)
        } else {
            p {
                text("Last Integration is from ")
                t.buildLink(job:my.project, number:ia.build.number)
                text(" ")
                text(_("ago",ia.build.timestampString))
            }
            p {
              int n = ia.upstreamBuildNumber
                if (n>=0) {
                    text("This integration is built into ")
                    t.buildLink(jobName:my.branchProperty.upstream, job:my.branchProperty.upstreamProject, number:n)
                } else {
                    text(_("No build has incorporated this integration yet."))
                }
            }
        }

        p(_("Subversion Revision")+":"+ia.integratedRevision)
    }
}
