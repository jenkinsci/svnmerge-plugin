package jenkins.plugins.svnmerge.FeatureBranchProperty
import lib.FormTagLib

def f = namespace(FormTagLib.class)

f.optionalBlock(name:"svnmerge", title:_("This project builds a Subversion feature branch"), checked:instance!=null, help:"/plugin/svnmerge/help/upstream.html") {
    f.nested {
        table(width:"100%") {
            f.entry(title:_("Upstream project name")) {
                f.editableComboBox (id: h.generateId(), field:"upstream", clazz:"setting-input") {
                    descriptor.listIntegratableProjects().each { u ->
                        f.editableComboBoxValue(value:u.name)
                    }
                }
            }
        }
    }
}
