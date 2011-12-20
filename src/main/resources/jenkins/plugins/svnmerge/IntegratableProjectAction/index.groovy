//  Show the last integration status
package jenkins.plugins.svnmerge.IntegratableProjectAction

def f = namespace(lib.FormTagLib.class)
def l = namespace(lib.LayoutTagLib.class)
def t = namespace(lib.JenkinsTagLib.class)

l.layout(norefresh: "true", title: _("title", my.project.displayName)) {
    include(my.project, "sidepanel")
    l.main_panel {
        h1 {
            img(src: "${rootURL}/plugin/svnmerge/48x48/sync.gif")
            text(_("Feature Branches"))
        }

        raw("<p>This project tracks integrations from branches via <tt>svn merge</tt></p>")

        def branches = my.branches;
        if (branches.size() > 0) {
            h2(_("Existing Feature Branches"))
            ul(style: "list-style:none") {
                branches.each { b ->
                    li {
                        t.jobLink(job: b)
                    }
                }
            }
        }

        h2(_("Create a new branch"))
        p(_("createBranchBlurb"))
        p {
            form(name: "new", method: "post", action: "newBranch") {
                table(class: "middle-align") {
                    tr {
                        td {
                            text(_("Branch Name") + ":")
                        }
                        td {
                            input(type: "text", name: "name", width: "30")
                        }
                    }
                    f.optionalBlock(name: "credential", title: _("Use alternate credential for branching")) {
                        f.block {
                            table(style: "width:100%") {
                                f.radioBlock(name: "kind", value: "password", title: _("Username/password authentication")) {
                                    f.entry(title: _("User name")) {
                                        f.textbox(name: "username1")
                                    }
                                    f.entry(title: _("Password")) {
                                        f.password(name: "password1")
                                    }
                                }

                                f.radioBlock(name: "kind", value: "publickey", title: _("SSH public key authentication (") + _("svn+ssh") + ")") {
                                    f.entry(title: _("User name")) {
                                        f.textbox(name: "username2")
                                    }

                                    f.entry(title: _("Pass phrase"), help: "/plugin/subversion/pass-phrase.html") {
                                        f.password(name: "password2")
                                    }
                                    f.entry(title: _("Private key}")) {
                                        input(type: "file", name: "privateKey", class: "setting-input")
                                    }
                                }

                                f.radioBlock(name: "kind", value: "certificate", title: _("HTTPS client certificate")) {
                                    f.entry(title: _("PKCS12 certificate")) {
                                        input(type: "file", name: "certificate", class: "setting-input")
                                    }
                                    f.entry(title: _("Password")) {
                                        f.password(name: "password3")
                                    }
                                }

                            }
                        }
                    }
                }
                f.submit(value: _("Create"))
            }
        }
    }
}
