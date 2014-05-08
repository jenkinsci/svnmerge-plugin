//  Show the last integration status
package jenkins.plugins.svnmerge.IntegratableProjectAction

import javax.swing.plaf.basic.BasicBorders.RadioButtonBorder;
import org.apache.commons.lang.StringUtils;

import static jenkins.plugins.svnmerge.RepositoryLayoutEnum.CUSTOM;

def f = namespace(lib.FormTagLib.class)
def l = namespace(lib.LayoutTagLib.class)
def t = namespace(lib.JenkinsTagLib.class)


l.layout(norefresh:"true",title:_("title",my.project.displayName)) {
    include(my.project, "sidepanel")
    l.main_panel {
        h1 {
            img (src:"${rootURL}/plugin/svnmerge/48x48/sync.gif")
            text(_("Feature Branches"))
        }

        raw("<p>This project tracks integrations from branches via <tt>svn merge</tt></p>")
		
		def repoLayout = my.repositoryLayout
		p(_("Repository URL: "+repoLayout.scmModuleLocation))
		p(_("Detected repository layout: "+repoLayout.layout))
		if (StringUtils.isNotEmpty(repoLayout.subProjectName)) {
			p(_("Detected subproject name: "+repoLayout.subProjectName))
		}

        def branches = my.branches;
        if (branches.size()>0) {
            h2(_("Existing Feature Branches"))
            ul(style:"list-style:none") {
                branches.each { b ->
                    li {
                        t.jobLink(job:b)
                    }
                }
            }
        }
		
        h2(_("Create a new branch"))
        p(_("createBranchBlurb"))
        p {
            form (name:"new", method:"post", action:"newBranch") {
				
                table (width: "100%") {

                    tr {
                        td (width: "25%") {
                            text(_("Branch Name")+":")
                        }
                        td {
                            input (type:"text", name:"name", size:"90")
                        }
                    }
                
                    tr {
                        td {
                            text(_("Commit Message")+":")
                        }
                        td {
                            input (type:"text", name:"commitMessage", size:"90")
                        }
                    }
					
					tr {
						td {
							text(_("Default new branch location")+":")
						}
						td {
							def value = repoLayout.defaultNewBranchUrl
							if (repoLayout.layout==CUSTOM) {
								value = _("unableToDetect","branches")
							}
							input (type:"text", name:"defaultNewBranchUrl", size:"90",
								   disabled: true, value: value)
						}
					}
                
                    tr {
                        td {
                             text(_("override", "branch")+":")
                        }
                        td {
                            input (type:"text", name:"branchLocation", size:"90")
                        }
                    }
                
                    tr {
                        td {
                            text(_("Create a development tag")+":")
                        }
                        td {
                            input (type:"checkbox", name:"createTag", width:"90")
                        }
                    }
					
					tr {
						td {
							text(_("Default new tag location")+":")
						}
						td {
							def value = repoLayout.defaultNewDevTagUrl
							if (repoLayout.layout==CUSTOM) {
								value = _("unableToDetect","tags")
							}
							input (type:"text", name:"defaultNewDevTagUrl", size:"90",
								   disabled: true, value: value)
						}
					}
                
                    tr {
                        td {
                            text(_("override", "tag")+":")
                        }
                        td {
                            input (type:"text", name:"tagLocation", size:"90")
                        }
                    }
                
                }
                
                input (type:"hidden", name:"layout", value: repoLayout.layout.name)
				
                f.submit(value:_("Create"))
            }
        }
		
		
    }
}
