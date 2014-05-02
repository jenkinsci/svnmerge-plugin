package jenkins.plugins.svnmerge;

import static jenkins.plugins.svnmerge.RepositoryLayoutEnum.CUSTOM;
import static jenkins.plugins.svnmerge.RepositoryLayoutEnum.MULTI;
import static jenkins.plugins.svnmerge.RepositoryLayoutEnum.SINGLE;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * This class models some properties useful for the creation of a new branch
 * by the {@link IntegratableProjectAction} class.
 * @author Verny Quartara Gutierrez
 *
 */
public class RepositoryLayoutInfo {
	
	private static final Pattern TRUNK_PATTERN = Pattern.compile("/trunk[/]?([\\w|_|-]*)");
	private static final Pattern BRANCHES_PATTERN = Pattern.compile("/branches/\\w+[/]?([\\w|_|-]*)");
	private static final short GROUP_INDEX = 1;
	private static final String ROOT_URL_SUFFIX = "/branches/<new_branch_name>/";
	
	private RepositoryLayoutEnum layout;
	private String scmModuleLocation;
	private String subProjectName;
	private String defaultNewBranchUrl;
	private String defaultNewDevTagUrl;

	/**
	 * The constructor accepts the scm module location and initialise
	 * the internal properties.
	 * @param scmModuleLocation subversion repository url
	 */
	public RepositoryLayoutInfo(String scmModuleLocation) {
		this.scmModuleLocation = StringUtils.removeEnd(scmModuleLocation.trim(), "/"); 
		Matcher trunkMatcher = TRUNK_PATTERN.matcher(this.scmModuleLocation);
		Matcher branchesMatcher = BRANCHES_PATTERN.matcher(this.scmModuleLocation);
		
		String matched = null; 
		String subProject = null;
		StringBuilder urlBuilder = new StringBuilder();
		if (trunkMatcher.find()) {
			matched = trunkMatcher.group();
			subProject = trunkMatcher.group(GROUP_INDEX);
			urlBuilder.append(scmModuleLocation.substring(0, trunkMatcher.start()));
		} else if (branchesMatcher.find()) {
			matched = branchesMatcher.group();
			subProject = branchesMatcher.group(GROUP_INDEX);
			urlBuilder.append(scmModuleLocation.substring(0, branchesMatcher.start()));
		}
		urlBuilder.append(ROOT_URL_SUFFIX);
		if (StringUtils.isNotEmpty(subProject)) {
			urlBuilder.append(subProject);
			urlBuilder.append("/");
		}
		this.defaultNewBranchUrl = urlBuilder.toString();
		this.defaultNewDevTagUrl = this.defaultNewBranchUrl.replace("/branches/", "/tags/dev/");
		setupLayout(matched, subProject);
	}

	private void setupLayout(String matched, String group) {
		if (StringUtils.isEmpty(matched) && StringUtils.isEmpty(group)) {
			this.layout = CUSTOM;
		} else if (StringUtils.isNotEmpty(group)) {
			if (scmModuleLocation.endsWith(group)) {
				this.layout = MULTI;
				this.subProjectName = group;
			} else {
				this.layout = CUSTOM;
			}
		} else {
			this.layout = SINGLE;
		}
	}

	public RepositoryLayoutEnum getLayout() {
		return layout;
	}

	public String getSubProjectName() {
		return subProjectName;
	}
	
	public String getScmModuleLocation() {
		return scmModuleLocation;
	}
	
	public String getDefaultNewDevTagUrl() {
		return defaultNewDevTagUrl;
	}

	public String getDefaultNewBranchUrl() {
		return defaultNewBranchUrl;
	}

}
