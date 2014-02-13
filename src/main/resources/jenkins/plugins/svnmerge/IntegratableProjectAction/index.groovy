//  Show the last integration status
package jenkins.plugins.svnmerge.IntegratableProjectAction

def f = namespace(lib.FormTagLib.class)
def l = namespace(lib.LayoutTagLib.class)
def t = namespace(lib.JenkinsTagLib.class)

l.layout(norefresh:"true",title:_("title",my.project.displayName)) {
    include(my.project, "sidepanel")
    l.main_panel {
        h1 {
            img (src:"${rootURL}/plugin/svnmerge/48x48/sync.gif")
            text(_("Feature Branches"))
        }

        raw("<p>This project tracks integrations from branches via <tt>svn merge</tt></p>")

        def branches = my.branches;
        if (branches.size()>0) {
            h2(_("Existing Feature Branches"))
            ul(style:"list-style:none") {
                branches.each { b ->
                    li {
                        t.jobLink(job:b)
                    }
                }
            }
        }

        h2(_("Create a new branch"))
        p(_("createBranchBlurb"))
        p {
            form (name:"new", method:"post", action:"newBranch") {
                text(_("Branch Name")+":")
                input (type:"text", name:"name", width:"30")
                raw("</br>")
                text(_("Commit Message")+":")
                input (type:"text", name:"commitMessage")
                raw("</br>")
                f.submit(value:_("Create"))
            }
        }
    }
}
