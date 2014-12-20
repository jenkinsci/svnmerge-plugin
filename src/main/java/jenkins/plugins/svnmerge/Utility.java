package jenkins.plugins.svnmerge;

import hudson.EnvVars; 
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

class Utility {

    /**
     * Get either the provided build of the root build of the provided
     * build if it is a promotion one.
     */
    static AbstractBuild<?,?> rootBuildOf(AbstractBuild build) {
        if (Jenkins.getInstance().getPlugin("promoted-builds") != null) {
            if (build instanceof hudson.plugins.promoted_builds.Promotion) {
                return build.getRootBuild();
            }
        }
        
        return build;
    }

	
    /**
     * Expands the system variables, the node environment variables and the project parameters
     */
	static ModuleLocation getExpandedLocation(ModuleLocation ml, Job<?,?> project) {
		 ModuleLocation location= ml.getExpandedLocation(project);
		// expand system variables
		Computer c = Computer.currentComputer();
		if (c != null) {
			try {
				// JVM vars
				EnvVars cEnv = c.getEnvironment();
				location = location.getExpandedLocation(cEnv);
				// global node vars
				for (NodeProperty<?> globalNodeProp : Jenkins.getInstance().getGlobalNodeProperties()) {
					if (globalNodeProp instanceof EnvironmentVariablesNodeProperty) {
						EnvVars nodeEnvVars = ((EnvironmentVariablesNodeProperty) globalNodeProp)
								.getEnvVars();
						location = location
								.getExpandedLocation(nodeEnvVars);

					}
				}
				
				// node vars
				for (NodeProperty<?> nodeProp : c.getNode().getNodeProperties()) {
					if (nodeProp instanceof EnvironmentVariablesNodeProperty) {
						EnvVars nodeEnvVars = ((EnvironmentVariablesNodeProperty) nodeProp)
								.getEnvVars();
						location = location
								.getExpandedLocation(nodeEnvVars);

					}
				}
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Failed to get computer environment",
						e);
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "Failed to get computer environment",
						e);

			}
		}
		// expand project variables
		if(project!=null){
			location = location.getExpandedLocation(project);
		}
		return location;
	}

    private static final Logger LOGGER = Logger.getLogger(Utility.class.getName());
}
