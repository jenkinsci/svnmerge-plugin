import lib.FormTagLib

def f = namespace(FormTagLib.class)

f.entry(title:_("Build to rebase to"), field:"permalink") {
    f.select()
}
f.entry(title:_("Stop the build if the merge fails"), field:"stopBuildIfMergeFails") {
	f.checkbox()
}
