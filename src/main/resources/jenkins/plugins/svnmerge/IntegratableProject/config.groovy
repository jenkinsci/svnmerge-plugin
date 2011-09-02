package jenkins.plugins.svnmerge.IntegratableProject

def f = namespace(lib.FormTagLib.class)

f.optionalBlock(name:"svnmerge_integratable", title:_("Accept Integration from Subversion feature branches"),
                 checked:instance!=null, help:"/plugin/svnmerge/help/integratable.html")
