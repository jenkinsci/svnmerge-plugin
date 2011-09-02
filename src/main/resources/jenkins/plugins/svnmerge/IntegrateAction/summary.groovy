package jenkins.plugins.svnmerge.IntegrateAction

def t = namespace(lib.JenkinsTagLib.class)

// Integration is complete. Display the record.
def n = my.upstreamBuildNumber
if (n>=0) {
    t.summary(icon:"/plugin/svnmerge/48x48/integrate.gif")
    text(_("This build is integrated into "))
    t.buildLink(jobName:my.property.upstream, job:my.property.upstreamProject, number:n)
}
