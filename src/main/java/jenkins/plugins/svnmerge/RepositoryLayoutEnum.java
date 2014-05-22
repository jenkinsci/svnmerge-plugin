package jenkins.plugins.svnmerge;

/**
 * Enumerates the different repository layout types.
 * @author Verny Quartara Gutierrez
 *
 */
public enum RepositoryLayoutEnum {
	
	/**
	 * Single project layout or multi project layout
	 * with non related projects.
	 */
	SINGLE("single project layout"),
	/**
	 * Multi close-related projects.
	 */
	MULTI("multi project layout"), 
	/**
	 * Custom layout not follwing the standard
	 * trunk-branches-tags layout.
	 */
	CUSTOM("custom layout");
	
	private String description;
	
	private RepositoryLayoutEnum(String description) {
		this.description = description;
	}
	
	public String getName() {
		return this.name();
	}

	@Override
	public String toString() {
		return description;
	}

}
