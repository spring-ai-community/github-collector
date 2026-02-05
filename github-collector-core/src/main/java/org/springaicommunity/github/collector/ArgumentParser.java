package org.springaicommunity.github.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line argument parser for GitHub Issues Collection application. Pure Java
 * implementation with no Spring dependencies for maximum testability.
 */

public class ArgumentParser {

	private final CollectionProperties defaultProperties;

	public ArgumentParser(CollectionProperties defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Parse command-line arguments and return configuration.
	 * @param args Command-line arguments
	 * @return Parsed configuration object
	 * @throws IllegalArgumentException if arguments are invalid
	 */
	public ParsedConfiguration parseAndValidate(String[] args) {
		ParsedConfiguration config = new ParsedConfiguration(defaultProperties);

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];

			switch (arg) {
				case "-r", "--repo":
					config.repository = getRequiredValue(args, i, "repository");
					i++; // Skip next argument since we consumed it
					break;

				case "-b", "--batch-size":
					String batchSizeStr = getRequiredValue(args, i, "batch-size");
					try {
						config.batchSize = Integer.parseInt(batchSizeStr);
						if (config.batchSize <= 0) {
							throw new IllegalArgumentException("Batch size must be positive: " + config.batchSize);
						}
					}
					catch (NumberFormatException e) {
						throw new IllegalArgumentException(
								"Invalid batch size '" + batchSizeStr + "': must be a positive integer");
					}
					i++; // Skip next argument since we consumed it
					break;

				case "-d", "--dry-run":
					config.dryRun = true;
					break;

				case "-i", "--incremental":
					config.incremental = true;
					break;

				case "-z", "--zip":
					config.zip = true;
					break;

				case "-v", "--verbose":
					config.verbose = true;
					break;

				case "--clean":
					config.clean = true;
					break;

				case "--no-clean", "--append":
					config.clean = false;
					break;

				case "--resume":
					config.resume = true;
					break;

				case "-s", "--state":
					String state = getRequiredValue(args, i, "state").toLowerCase();
					if (!List.of("open", "closed", "all").contains(state)) {
						throw new IllegalArgumentException(
								"Invalid state '" + state + "': must be 'open', 'closed', or 'all'");
					}
					config.issueState = state;
					i++; // Skip next argument since we consumed it
					break;

				case "-l", "--labels":
					String labelStr = getRequiredValue(args, i, "labels");
					config.labelFilters = Arrays.stream(labelStr.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
					i++; // Skip next argument since we consumed it
					break;

				case "--label-mode":
					String labelMode = getRequiredValue(args, i, "label-mode").toLowerCase();
					if (!List.of("any", "all").contains(labelMode)) {
						throw new IllegalArgumentException(
								"Invalid label mode '" + labelMode + "': must be 'any' or 'all'");
					}
					config.labelMode = labelMode;
					i++; // Skip next argument since we consumed it
					break;

				case "--max-issues":
					String maxIssuesStr = getRequiredValue(args, i, "max-issues");
					try {
						config.maxIssues = Integer.parseInt(maxIssuesStr);
						if (config.maxIssues <= 0) {
							throw new IllegalArgumentException("Max issues must be positive: " + config.maxIssues);
						}
					}
					catch (NumberFormatException e) {
						throw new IllegalArgumentException(
								"Invalid max issues '" + maxIssuesStr + "': must be a positive integer");
					}
					i++; // Skip next argument since we consumed it
					break;

				case "--sort-by":
					String sortBy = getRequiredValue(args, i, "sort-by").toLowerCase();
					if (!List.of("updated", "created", "comments", "reactions").contains(sortBy)) {
						throw new IllegalArgumentException("Invalid sort field '" + sortBy
								+ "': must be 'updated', 'created', 'comments', or 'reactions'");
					}
					config.sortBy = sortBy;
					i++; // Skip next argument since we consumed it
					break;

				case "--sort-order":
					String sortOrder = getRequiredValue(args, i, "sort-order").toLowerCase();
					if (!List.of("desc", "asc").contains(sortOrder)) {
						throw new IllegalArgumentException(
								"Invalid sort order '" + sortOrder + "': must be 'desc' or 'asc'");
					}
					config.sortOrder = sortOrder;
					i++; // Skip next argument since we consumed it
					break;

				case "-t", "--type":
					String collectionType = getRequiredValue(args, i, "type").toLowerCase();
					if (!List.of("issues", "prs", "collaborators", "releases").contains(collectionType)) {
						throw new IllegalArgumentException("Invalid collection type '" + collectionType
								+ "': must be 'issues', 'prs', 'collaborators', or 'releases'");
					}
					config.collectionType = collectionType;
					i++; // Skip next argument since we consumed it
					break;

				case "-n", "--number":
					String numberStr = getRequiredValue(args, i, "number");
					try {
						config.prNumber = Integer.parseInt(numberStr);
						if (config.prNumber <= 0) {
							throw new IllegalArgumentException("PR number must be positive: " + config.prNumber);
						}
					}
					catch (NumberFormatException e) {
						throw new IllegalArgumentException(
								"Invalid PR number '" + numberStr + "': must be a positive integer");
					}
					i++; // Skip next argument since we consumed it
					break;

				case "--pr-state":
					String prState = getRequiredValue(args, i, "pr-state").toLowerCase();
					if (!List.of("open", "closed", "all", "merged").contains(prState)) {
						throw new IllegalArgumentException(
								"Invalid PR state '" + prState + "': must be 'open', 'closed', 'merged', or 'all'");
					}
					config.prState = prState;
					i++; // Skip next argument since we consumed it
					break;

				case "--created-after":
					config.createdAfter = getRequiredValue(args, i, "created-after");
					if (!config.createdAfter.matches("\\d{4}-\\d{2}-\\d{2}")) {
						throw new IllegalArgumentException(
								"Invalid date '" + config.createdAfter + "': must be YYYY-MM-DD format");
					}
					i++;
					break;

				case "--created-before":
					config.createdBefore = getRequiredValue(args, i, "created-before");
					if (!config.createdBefore.matches("\\d{4}-\\d{2}-\\d{2}")) {
						throw new IllegalArgumentException(
								"Invalid date '" + config.createdBefore + "': must be YYYY-MM-DD format");
					}
					i++;
					break;

				case "--single-file":
					config.singleFile = true;
					break;

				case "-o", "--output":
					config.outputFile = getRequiredValue(args, i, "output");
					i++; // Skip next argument since we consumed it
					break;

				case "--verify":
					config.verify = true;
					break;

				case "--deduplicate":
					config.deduplicate = true;
					config.verify = true; // deduplicate implies verify
					break;

				case "--verify-dir":
					config.verifyDir = getRequiredValue(args, i, "verify-dir");
					i++;
					break;

				case "-h", "--help":
					config.helpRequested = true;
					break;

				default:
					if (arg.startsWith("-")) {
						throw new IllegalArgumentException("Unknown option: " + arg);
					}
					break;
			}
		}

		// Validate configuration
		validateConfiguration(config);

		return config;
	}

	/**
	 * Check if help is requested without full parsing.
	 * @param args Command-line arguments
	 * @return true if help is requested
	 */
	public boolean isHelpRequested(String[] args) {
		for (String arg : args) {
			if ("-h".equals(arg) || "--help".equals(arg)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Generate help text for command-line usage.
	 * @return Help text string
	 */
	public String generateHelpText() {
		StringBuilder help = new StringBuilder();
		help.append("Usage: collect_github_issues.java [OPTIONS]\n");
		help.append("\n");
		help.append("Collect GitHub issues from a repository with advanced filtering capabilities.\n");
		help.append("\n");
		help.append("OPTIONS:\n");
		help.append("    -h, --help              Show this help message\n");
		help.append("    -r, --repo REPO         Repository in format owner/repo (default: ")
			.append(defaultProperties.getDefaultRepository())
			.append(")\n");
		help.append("    -b, --batch-size SIZE   Issues per batch file (default: ")
			.append(defaultProperties.getBatchSize())
			.append(")\n");
		help.append("    -d, --dry-run          Show what would be collected without doing it\n");
		help.append("    -i, --incremental      Skip already collected issues\n");
		help.append("    -z, --zip              Create zip archive of collected data\n");
		help.append("    -v, --verbose          Enable verbose logging\n");
		help.append("    --clean                Clean up previous collection data before starting (default)\n");
		help.append("    --no-clean, --append  Keep previous collection data and append new data\n");
		help.append("    --resume               Resume from last successful batch\n");
		help.append("\n");
		help.append("FILTERING OPTIONS:\n");
		help.append("    -s, --state <state>     Issue state: open, closed, all (default: ")
			.append(defaultProperties.getDefaultState())
			.append(")\n");
		help.append("    -l, --labels <labels>   Comma-separated list of labels to filter by\n");
		help.append("    --label-mode <mode>     Label matching mode: any, all (default: ")
			.append(defaultProperties.getDefaultLabelMode())
			.append(")\n");
		help.append("                           Note: 'any' mode uses first label only due to API limitations\n");
		help.append("\n");
		help.append("COLLECTION TYPE OPTIONS:\n");
		help.append(
				"    -t, --type <type>       Collection type: issues, prs, collaborators, releases (default: issues)\n");
		help.append("    -n, --number <number>   Specific PR number to collect (when type=prs)\n");
		help.append("    --pr-state <state>      PR state: open, closed, merged, all (default: open)\n");
		help.append("\n");
		help.append("DATE FILTERING OPTIONS:\n");
		help.append("    --created-after DATE    Only collect issues created on or after DATE (YYYY-MM-DD)\n");
		help.append("    --created-before DATE   Only collect issues created before DATE (YYYY-MM-DD)\n");
		help.append("                           Filters via GitHub search API; also stops pagination early\n");
		help.append("\n");
		help.append("DASHBOARD OPTIONS (Phase 1 Enhancement):\n");
		help.append("    --max-issues <count>    Limit total issues collected (default: unlimited)\n");
		help.append(
				"    --sort-by <field>       Sort field: updated, created, comments, reactions (default: updated)\n");
		help.append("    --sort-order <order>    Sort order: desc, asc (default: desc)\n");
		help.append("                           Use these for dashboard queries like '20 most recent issues'\n");
		help.append("\n");
		help.append("CONFIGURATION:\n");
		help.append("    Configuration can be customized via application.yaml file\n");
		help.append("    All settings under 'github.issues' prefix can be overridden\n");
		help.append("    Command-line arguments take precedence over configuration file\n");
		help.append("\n");
		help.append("ENVIRONMENT VARIABLES:\n");
		help.append("    GITHUB_TOKEN           GitHub personal access token (required)\n");
		help.append("\n");
		help.append("EXAMPLES:\n");
		help.append("    # Basic usage\n");
		help.append("    ./collect_github_issues.java --repo spring-projects/spring-ai\n");
		help.append("\n");
		help.append("    # State filtering\n");
		help.append("    ./collect_github_issues.java --state open --dry-run\n");
		help.append("    ./collect_github_issues.java --state all --batch-size 50\n");
		help.append("\n");
		help.append("    # Label filtering\n");
		help.append("    ./collect_github_issues.java --labels bug\n");
		help.append("    ./collect_github_issues.java --labels bug --no-clean  # Keep previous data\n");
		help.append("    ./collect_github_issues.java --labels \"bug,priority:high\" --label-mode all\n");
		help.append("\n");
		help.append("    # Combined filtering\n");
		help.append("    ./collect_github_issues.java --state open --labels bug --verbose\n");
		help.append("    ./collect_github_issues.java --state closed --labels documentation,enhancement\n");
		help.append("\n");
		help.append("    # Dashboard use cases (Phase 1 Enhancement)\n");
		help.append("    ./collect_github_issues.java --max-issues 20 --sort-by updated --sort-order desc\n");
		help.append("    ./collect_github_issues.java --max-issues 50 --sort-by created --state open\n");
		help.append(
				"    ./collect_github_issues.java --max-issues 10 --sort-by comments --sort-order desc --labels bug\n");
		help.append("\n");
		help.append("    # PR collection\n");
		help.append("    ./collect_github_issues.java --type prs --repo spring-projects/spring-ai\n");
		help.append("    ./collect_github_issues.java --type prs --number 4347 --dry-run  # Specific PR\n");
		help.append("    ./collect_github_issues.java --type prs --pr-state merged --max-issues 10\n");
		help.append("\n");
		help.append("    # Collaborator collection (for maintainer identification)\n");
		help.append("    ./collect_github_issues.java --type collaborators --repo spring-projects/spring-ai\n");
		help.append("    ./collect_github_issues.java --type collaborators --repo owner/repo --dry-run\n");
		help.append("\n");
		help.append("    # Releases collection (for release notes analysis)\n");
		help.append("    ./collect_github_issues.java --type releases --repo spring-projects/spring-ai\n");
		help.append("\n");
		help.append("OUTPUT OPTIONS:\n");
		help.append("    --single-file           Output all results to a single JSON file\n");
		help.append("    -o, --output <file>     Output file path (default: all_prs.json or all_issues.json)\n");
		help.append("\n");
		help.append("    # Single file output examples\n");
		help.append("    ./collect_github_issues.java --type prs --pr-state open --single-file -o all_prs.json\n");
		help.append(
				"    ./collect_github_issues.java --type prs --single-file --incremental --no-clean -o all_prs.json\n");
		help.append("\n");
		help.append("VERIFICATION OPTIONS:\n");
		help.append("    --verify                Verify batch files for duplicates, date-range violations,\n");
		help.append("                           state mismatches, and batch integrity issues\n");
		help.append("    --deduplicate           Remove duplicates from batch files (implies --verify)\n");
		help.append("    --verify-dir <dir>      Directory containing batch files (for standalone verification)\n");
		help.append("\n");
		help.append("    # Standalone verification (no collection, no GITHUB_TOKEN needed)\n");
		help.append(
				"    ./collect_github_issues.java --type issues --verify --verify-dir data/expanded/project/issues\n");
		help.append("    ./collect_github_issues.java --type prs --deduplicate --verify-dir data/raw/prs\n");
		help.append("\n");

		return help.toString();
	}

	/**
	 * Validate environment (GitHub token, etc.)
	 * @throws IllegalStateException if environment is invalid
	 */
	public void validateEnvironment() {
		// Check for GitHub token
		String githubToken = EnvironmentSupport.get("GITHUB_TOKEN");
		if (githubToken == null || githubToken.trim().isEmpty()) {
			throw new IllegalStateException(
					"GITHUB_TOKEN environment variable is required. Please set your GitHub personal access token: export GITHUB_TOKEN=your_token_here");
		}
	}

	private String getRequiredValue(String[] args, int currentIndex, String optionName) {
		if (currentIndex + 1 >= args.length) {
			throw new IllegalArgumentException("Missing value for " + optionName + " option");
		}
		return args[currentIndex + 1];
	}

	private void validateConfiguration(ParsedConfiguration config) {
		List<String> errors = new ArrayList<>();

		// Validate repository format
		if (config.repository == null || config.repository.trim().isEmpty()) {
			errors.add("Repository cannot be empty");
		}
		else if (!config.repository.matches("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$")) {
			errors.add("Repository must be in format 'owner/repo' (e.g., 'spring-projects/spring-ai')");
		}

		// Validate batch size
		if (config.batchSize <= 0) {
			errors.add("Batch size must be positive (got: " + config.batchSize + ")");
		}
		else if (config.batchSize > 1000) {
			errors.add("Batch size too large (got: " + config.batchSize + ", max: 1000)");
		}

		// Validate issue state
		if (!List.of("open", "closed", "all").contains(config.issueState.toLowerCase())) {
			errors.add("Invalid issue state: " + config.issueState + " (must be 'open', 'closed', or 'all')");
		}

		// Validate label mode
		if (!List.of("any", "all").contains(config.labelMode.toLowerCase())) {
			errors.add("Invalid label mode: " + config.labelMode + " (must be 'any' or 'all')");
		}

		// Validate collection type
		if (!List.of("issues", "prs", "collaborators", "releases").contains(config.collectionType.toLowerCase())) {
			errors.add("Invalid collection type: " + config.collectionType
					+ " (must be 'issues', 'prs', 'collaborators', or 'releases')");
		}

		// Validate PR-specific parameters
		if ("prs".equals(config.collectionType)) {
			if (!List.of("open", "closed", "merged", "all").contains(config.prState.toLowerCase())) {
				errors.add("Invalid PR state: " + config.prState + " (must be 'open', 'closed', 'merged', or 'all')");
			}

			if (config.prNumber != null && config.prNumber <= 0) {
				errors.add("PR number must be positive (got: " + config.prNumber + ")");
			}
		}

		// Report validation errors
		if (!errors.isEmpty()) {
			StringBuilder errorMsg = new StringBuilder("Configuration validation failed:");
			for (String error : errors) {
				errorMsg.append("\n  - ").append(error);
			}
			throw new IllegalArgumentException(errorMsg.toString());
		}
	}

}