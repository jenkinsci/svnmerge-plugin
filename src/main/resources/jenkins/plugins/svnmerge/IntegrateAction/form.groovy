package jenkins.plugins.svnmerge.IntegrateAction

def f = namespace(lib.FormTagLib.class)
def l = namespace(lib.LayoutTagLib.class)
def t = namespace(lib.JenkinsTagLib.class)
def st = namespace("jelly:stapler")

l.layout(norefresh:"true", title:"#${my.build.number} Integration") {
    include(my.build, "sidepanel")
    l.main_panel {
        img(src:"${rootURL}/plugin/svnmerge/48x48/integrate.gif")
        text(_("Integrate {0} to upstream", my.build.displayName))
    }

    p {
        text("This will merge ${my.svnInfo} to")
        t.jobLink(job:my.property.upstreamProject)
    }

    form(action:"perform", method:"post", name:"integrate") {
        f.submit(value:_("Integrate this build to upstream"))
    }

    if (my.integratedRevision<0) {
        h2(style:"margin-top:2em", _("Last Failure"))
        pre {
            st.copyStream(reader:my.log.readAll())
        }
    }
}
