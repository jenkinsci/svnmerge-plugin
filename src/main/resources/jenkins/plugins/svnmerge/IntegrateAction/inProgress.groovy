package jenkins.plugins.svnmerge.IntegrateAction

def l = namespace(lib.LayoutTagLib.class)

// Integration is complete. Display the record.
l.layout(norefresh:"true", title:"#${my.build.number} Integration") {
    include(my.build, "sidepanel")
    l.main_panel {
        h1 {
            img(src:"${rootURL}/plugin/svnmerge/48x48/integrate.gif")
            text(_("title", my.integratedRevision))
        }

        p(_("Integration is in progress."))

        include(my,"log")
    }
}
