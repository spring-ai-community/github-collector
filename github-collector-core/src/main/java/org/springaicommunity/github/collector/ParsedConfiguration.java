package org.springaicommunity.github.collector;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed configuration result from command-line arguments.
 */
public class ParsedConfiguration {

	// Repository settings
	public String repository;

	// Batch configuration
	public int batchSize;

	// Mode flags
	public boolean dryRun = false;

	public boolean incremental = false;

	public boolean zip = false;

	public boolean verbose = false;

	public boolean clean = true; // Default to clean mode

	public boolean resume = false;

	public boolean helpRequested = false;

	// Issue filtering
	public String issueState;

	public List<String> labelFilters = new ArrayList<>();

	public String labelMode;

	// Dashboard enhancement parameters (Phase 1)
	public Integer maxIssues; // null = unlimited

	public String sortBy; // updated/created/comments/reactions

	public String sortOrder; // desc/asc

	// Collection type (Phase 2: PR Collection)
	public String collectionType = "issues"; // issues or prs

	public Integer prNumber; // specific PR number to collect

	public String prState; // open/closed/all for PR filtering

	// Output options (single-file mode)
	public boolean singleFile = false; // output all results to a single JSON file

	public String outputFile = null; // custom output file path

	public ParsedConfiguration(CollectionProperties defaultProperties) {
		// Initialize with defaults
		this.repository = defaultProperties.getDefaultRepository();
		this.batchSize = defaultProperties.getBatchSize();
		this.issueState = defaultProperties.getDefaultState();
		this.labelMode = defaultProperties.getDefaultLabelMode();
		this.verbose = defaultProperties.isVerbose();

		// Dashboard parameters - set defaults
		this.maxIssues = null; // unlimited by default (backward compatible)
		this.sortBy = "updated"; // default GitHub sort
		this.sortOrder = "desc"; // most recent first

		// PR collection parameters - set defaults
		this.collectionType = "issues"; // default to issue collection
		this.prNumber = null; // null = collect all PRs (when type=prs)
		this.prState = "open"; // default PR state
	}

	@Override
	public String toString() {
		return "ParsedConfiguration{" + "repository='" + repository + '\'' + ", batchSize=" + batchSize + ", dryRun="
				+ dryRun + ", incremental=" + incremental + ", zip=" + zip + ", verbose=" + verbose + ", clean=" + clean
				+ ", resume=" + resume + ", helpRequested=" + helpRequested + ", issueState='" + issueState + '\''
				+ ", labelFilters=" + labelFilters + ", labelMode='" + labelMode + '\'' + ", maxIssues=" + maxIssues
				+ ", sortBy='" + sortBy + '\'' + ", sortOrder='" + sortOrder + '\'' + ", collectionType='"
				+ collectionType + '\'' + ", prNumber=" + prNumber + ", prState='" + prState + '\'' + ", singleFile="
				+ singleFile + ", outputFile='" + outputFile + '\'' + '}';
	}

}