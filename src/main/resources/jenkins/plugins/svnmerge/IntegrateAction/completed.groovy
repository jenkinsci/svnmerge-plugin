package jenkins.plugins.svnmerge.IntegrateAction

def l = namespace(lib.LayoutTagLib.class)
def t = namespace(lib.JenkinsTagLib.class)
def st = namespace("jelly:stapler")

// Integration is complete. Display the record.
l.layout(norefresh:"true", title:"#${my.build.number} Integration") {
    include(my.build, "sidepanel")
    l.main_panel {
        h1 {
            img(src:"${rootURL}/plugin/svnmerge/48x48/integrate.gif")
            text(_("title", my.integratedRevision))
        }

        n = my.upstreamBuildNumber
        if (n>=0) {
            div (style:"margin:1em") {
                text(_("This integration is built into"))
                t.buildLink(jobName:my.property.upstream, job:my.property.upstreamProject, number:n)
            }
        }

        h2(_("Log"))
        pre {
            st.copyStream(reader:my.log.readAll())
        }
    }
}
