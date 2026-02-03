package org.springaicommunity.github.collector.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.github.collector.*;

/**
 * GitHub Collector CLI Application
 *
 * Plain Java command-line application to collect GitHub issues, PRs, and collaborators
 * from a repository. No Spring dependencies - uses GitHubCollectorBuilder for service
 * wiring.
 *
 * Usage: java -jar github-collector-cli.jar [OPTIONS]
 *
 * Environment Variables: GITHUB_TOKEN - GitHub personal access token for authentication
 *
 * Examples: java -jar github-collector-cli.jar --repo spring-projects/spring-ai java -jar
 * github-collector-cli.jar --batch-size 50 --incremental java -jar
 * github-collector-cli.jar --dry-run --verbose java -jar github-collector-cli.jar --type
 * prs --pr-state merged java -jar github-collector-cli.jar --type collaborators
 */
public class GitHubCollectorCli {

	private static final Logger logger = LoggerFactory.getLogger(GitHubCollectorCli.class);

	public static void main(String[] args) {
		try {
			run(args);
		}
		catch (Exception e) {
			logger.error("Collection failed: {}", e.getMessage());
			System.exit(1);
		}
	}

	public static void run(String[] args) throws Exception {
		// Create argument parser with default properties
		CollectionProperties properties = new CollectionProperties();
		ArgumentParser argumentParser = new ArgumentParser(properties);

		// Check for help request first
		if (argumentParser.isHelpRequested(args)) {
			System.out.println(argumentParser.generateHelpText());
			return;
		}

		// Parse and validate arguments
		ParsedConfiguration config = argumentParser.parseAndValidate(args);
		argumentParser.validateEnvironment();

		// Log configuration
		logConfiguration(config);

		// Build the appropriate collector using the builder
		GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().tokenFromEnv().properties(properties);

		// Execute collection based on type
		CollectionResult result;
		switch (config.collectionType) {
			case "prs":
				PRCollectionService prCollector = builder.buildPRCollector();
				result = prCollector.collectItems(createRequest(config));
				break;
			case "collaborators":
				CollaboratorsCollectionService collaboratorsCollector = builder.buildCollaboratorsCollector();
				result = collaboratorsCollector.collectItems(createRequest(config));
				break;
			default:
				IssueCollectionService issueCollector = builder.buildIssueCollector();
				result = issueCollector.collectItems(createRequest(config));
				break;
		}

		// Log results
		logResults(result, config.verbose);
	}

	private static CollectionRequest createRequest(ParsedConfiguration config) {
		return new CollectionRequest(config.repository, config.batchSize, config.dryRun, config.incremental, config.zip,
				config.clean, config.resume, config.issueState, config.labelFilters, config.labelMode, config.maxIssues,
				config.sortBy, config.sortOrder, config.collectionType, config.prNumber, config.prState, config.verbose,
				config.singleFile, config.outputFile);
	}

	private static void logConfiguration(ParsedConfiguration config) {
		logger.info("Configuration:");
		logger.info("  Repository: {}", config.repository);
		logger.info("  Batch size: {}", config.batchSize);
		logger.info("  Dry run: {}", config.dryRun);
		logger.info("  Incremental: {}", config.incremental);
		logger.info("  Zip: {}", config.zip);
		logger.info("  Verbose: {}", config.verbose);
		logger.info("  Clean: {}", config.clean);
		logger.info("  Resume: {}", config.resume);
		logger.info("  Issue state: {}", config.issueState);
		logger.info("  Label filters: {}", config.labelFilters);
		logger.info("  Label mode: {}", config.labelMode);
		logger.info("  Max issues: {}", config.maxIssues != null ? config.maxIssues : "unlimited");
		logger.info("  Sort by: {}", config.sortBy);
		logger.info("  Sort order: {}", config.sortOrder);
		logger.info("  Collection type: {}", config.collectionType);
		logger.info("  PR number: {}", config.prNumber != null ? config.prNumber : "all");
		logger.info("  PR state: {}", config.prState);
		logger.info("  Single file: {}", config.singleFile);
		logger.info("  Output file: {}", config.outputFile != null ? config.outputFile : "(default)");
	}

	private static void logResults(CollectionResult result, boolean verbose) {
		logger.info("Collection completed successfully!");
		logger.info("Total items: {}", result.totalIssues());
		logger.info("Processed items: {}", result.processedIssues());
		logger.info("Output directory: {}", result.outputDirectory());
		logger.info("Batch files created: {}", result.batchFiles().size());

		if (verbose && !result.batchFiles().isEmpty()) {
			logger.info("Batch files:");
			for (String batchFile : result.batchFiles()) {
				logger.info("  - {}", batchFile);
			}
		}
	}

}
