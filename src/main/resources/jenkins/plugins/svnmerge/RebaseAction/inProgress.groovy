package jenkins.plugins.svnmerge.RebaseAction

def l = namespace(lib.LayoutTagLib.class)

l.layout(norefresh:"true", title:_("Rebase changes from upstream")) {
    include(my.project, "sidepanel")

    l.main_panel {
        h1 {
            img(src:"${rootURL}/plugin/svnmerge/48x48/sync.gif")
            text(_("Rebase changes from upstream"))
        }

        p(_("Rebasing is in progress"))

        include(my,"log")
    }
}
