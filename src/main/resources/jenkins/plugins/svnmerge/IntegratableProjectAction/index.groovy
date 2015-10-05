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
                        td (class: "setting-leftspace")
                        td (class: "setting-name") {
                            text(_("Branch Name")+":")
                        }
                        td (class: "setting-main") {
                            input (type: "text", name: "name", class:"setting-input")
                        }
                    }
  
                    tr {
                        td (class: "setting-leftspace")
                        td (class: "setting-name") {
                            text(_("Commit Message")+":")
                        }
                        td (class: "setting-main") {
                            input (type:"text", name:"commitMessage", class:"setting-input")
                        }
                    }
					
                    tr {
                        td (class: "setting-leftspace")
                        td (class: "setting-name") {
                             text(_("Branch location")+":")
                        }
                        td (class: "setting-main") {
                            input (type:"text", name:"branchLocation", class:"setting-input")
                        }
                    }
                				
                    if (repoLayout.defaultNewBranchUrl) {
                    
                        tr {
                            td (colspan: 2)
                            td (class: "setting-description") {
                                text(_("leaveBlankToUseDefault", repoLayout.defaultNewBranchUrl))
                            }
                        }
                    
                    }
                
                    tr {
                        td (colspan: 3) {
                            input (type: "checkbox", name: "createTag")
                            label (class: "attach-previous") {
                                text(_("Create a development tag"))
                            }
                        }
                    }

                    tr {
                        td (class: "setting-leftspace")
                        td (class: "setting-name") {
                            text(_("Tag location")+":")
                        }
                        td (class: "setting-main") {
                            input (type:"text", name:"tagLocation", class:"setting-input")
                        }
                    }
                				
                    if (repoLayout.defaultNewDevTagUrl) {
                    
                        tr {
                            td (colspan: 2)
                            td (class: "setting-description") {
                                text(_("leaveBlankToUseDefault", repoLayout.defaultNewDevTagUrl))
                            }
                        }
                    
                    }

                    tr {
                        td (class: "setting-leftspace")
                        td (class: "setting-name") {
                            text(_("Rebase commit prefix")+":")
                        }
                        td (class: "setting-main") {
                            input (type:"text", name:"rebaseCommitPrefix", class:"setting-input")
                        }
                    }
                
                }
				
                f.submit(value:_("Create"))
            }
        }
		
		
    }
}
