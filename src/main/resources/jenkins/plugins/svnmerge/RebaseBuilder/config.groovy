import lib.FormTagLib

def f = namespace(FormTagLib.class)

f.entry(title:_("Build to rebase to"), field:"permalink") {
    f.select()
}
f.entry(title:_("Stop the build if the merge fails"), field:"stopBuildIfMergeFails") {
	f.checkbox()
}
f.entry(title:_("Make the build UNSTABLE if the merge fails"), field:"setUnstableIfMergeFails") {
	f.checkbox()
}
