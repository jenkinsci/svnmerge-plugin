import lib.FormTagLib

def f = namespace(FormTagLib.class)

f.entry(title:_("Build to rebase to"), field:"permalink") {
    f.select()
}
