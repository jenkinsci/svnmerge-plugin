package jenkins.plugins.svnmerge.IntegrateAction

if (my.integrationAttempted) {
    a (href:"${rootURL}/${my.build.url}integrate-branch/") {
        if (my.integratedRevision<0) {
            img (width:16, height:16, tooltip:_("Integration Failed"),
                    alt:"[integration failed]",
                    src:"${rootURL}/plugin/svnmerge/16x16/failed-integrate.gif")
        } else
        if (my.integratedRevision>0) {
            img (width:16, height:16, tooltip:_("Integrated"),
                    alt:"[integrated]",
                    src:"${rootURL}/plugin/svnmerge/16x16/integrate.gif")
        }
        // if integratedRevision==0, that means there was no commit, so skip the badge
    }
}
