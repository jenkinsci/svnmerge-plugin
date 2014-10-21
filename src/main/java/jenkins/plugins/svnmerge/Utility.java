package jenkins.plugins.svnmerge;

import hudson.EnvVars; 
import hudson.model.Computer;
import hudson.model.Job;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utility {

	
    /**
     * Expands the system variables, the node environment variables and the project parameters
     */
	public static ModuleLocation getExpandedLocation(ModuleLocation ml, Job<?,?> project) {
		 ModuleLocation location= ml.getExpandedLocation(project);
		// expand system variables
		Computer c = Computer.currentComputer();
		if (c != null) {
			try {
				// JVM vars
				EnvVars cEnv = c.getEnvironment();
				location = location.getExpandedLocation(cEnv);
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
