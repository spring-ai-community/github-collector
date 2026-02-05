package org.springaicommunity.github.collector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.github.collector.*;

import java.nio.file.Path;
import java.nio.file.Paths;

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
 * prs --pr-state merged java -jar github-collector-cli.jar --type collaborators java -jar
 * github-collector-cli.jar --type releases java -jar github-collector-cli.jar --type
 * issues --repo owner/repo --verify java -jar github-collector-cli.jar --type prs --repo
 * owner/repo --deduplicate
 */
public class GitHubCollectorCli {

	private static final Logger logger = LoggerFactory.getLogger(GitHubCollectorCli.class);

	public static void main(String[] args) {
		try {
			int exitCode = run(args);
			if (exitCode != 0) {
				System.exit(exitCode);
			}
		}
		catch (Exception e) {
			logger.error("Collection failed: {}", e.getMessage());
			System.exit(1);
		}
	}

	public static int run(String[] args) throws Exception {
		// Create argument parser with default properties
		CollectionProperties properties = new CollectionProperties();
		ArgumentParser argumentParser = new ArgumentParser(properties);

		// Check for help request first
		if (argumentParser.isHelpRequested(args)) {
			System.out.println(argumentParser.generateHelpText());
			return 0;
		}

		// Parse and validate arguments
		ParsedConfiguration config = argumentParser.parseAndValidate(args);

		// Standalone verification mode: --verify without collection intent
		if (config.verify && !hasCollectionIntent(config)) {
			logConfiguration(config);
			return runStandaloneVerification(config);
		}

		// Collection mode: requires GITHUB_TOKEN
		argumentParser.validateEnvironment();

		// Log configuration
		logConfiguration(config);

		// Build the appropriate collector using the builder
		GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().tokenFromEnv().properties(properties);

		// Execute collection based on type
		CollectionRequest request = createRequest(config);
		boolean useWindowing = config.createdAfter != null && config.createdBefore != null;
		CollectionResult result;
		switch (config.collectionType) {
			case "prs":
				if (useWindowing) {
					logger.info("Adaptive time-window splitting enabled for PR collection");
					result = builder.buildWindowedPRCollector(request).collectItems(request);
				}
				else {
					result = builder.buildPRCollector().collectItems(request);
				}
				break;
			case "collaborators":
				result = builder.buildCollaboratorsCollector().collectItems(request);
				break;
			case "releases":
				result = builder.buildReleasesCollector().collectItems(request);
				break;
			default:
				if (useWindowing) {
					logger.info("Adaptive time-window splitting enabled for issue collection");
					result = builder.buildWindowedIssueCollector(request).collectItems(request);
				}
				else {
					result = builder.buildIssueCollector().collectItems(request);
				}
				break;
		}

		// Log results
		logResults(result, config.verbose);

		// Post-collection verification
		if (config.verify) {
			return runPostCollectionVerification(config, result);
		}

		return 0;
	}

	/**
	 * Returns true if the configuration indicates the user wants to collect data (not
	 * just verify existing batches).
	 */
	private static boolean hasCollectionIntent(ParsedConfiguration config) {
		// If verifyDir is set, user explicitly wants standalone verification
		if (config.verifyDir != null) {
			return false;
		}
		return config.createdAfter != null || config.createdBefore != null || config.dryRun;
	}

	/**
	 * Run verification against existing batch files (no collection, no GITHUB_TOKEN
	 * needed).
	 */
	private static int runStandaloneVerification(ParsedConfiguration config) throws Exception {
		Path outputDir;
		if (config.verifyDir != null) {
			outputDir = Paths.get(config.verifyDir);
		}
		else {
			String state = "prs".equals(config.collectionType) ? config.prState : config.issueState;
			outputDir = deriveOutputDirectory(config.collectionType, config.repository, state);
		}

		logger.info("Standalone verification mode");
		logger.info("  Output directory: {}", outputDir);

		return runVerification(config, outputDir);
	}

	/**
	 * Run verification after a collection operation.
	 */
	private static int runPostCollectionVerification(ParsedConfiguration config, CollectionResult result)
			throws Exception {
		Path outputDir = Paths.get(result.outputDirectory());
		logger.info("Post-collection verification");
		return runVerification(config, outputDir);
	}

	/**
	 * Common verification logic used by both standalone and post-collection modes.
	 */
	private static int runVerification(ParsedConfiguration config, Path outputDir) throws Exception {
		ObjectMapper objectMapper = ObjectMapperFactory.create();
		BatchVerificationService verifier = new BatchVerificationService(objectMapper);

		String state = "prs".equals(config.collectionType) ? config.prState : config.issueState;

		VerificationResult result = verifier.verify(outputDir, config.collectionType, state, config.createdAfter,
				config.createdBefore);

		logVerificationResult(result);

		if (result.passed()) {
			logger.info("Verification PASSED");
			return 0;
		}

		// Verification failed — optionally deduplicate
		if (config.deduplicate && !result.duplicates().isEmpty()) {
			logger.info("Running deduplication...");
			BatchDeduplicationService deduplicator = new BatchDeduplicationService(objectMapper);
			DeduplicationResult dedupResult = deduplicator.deduplicate(outputDir, config.collectionType,
					result.duplicates());
			logDeduplicationResult(dedupResult);

			// Re-verify after deduplication
			VerificationResult recheck = verifier.verify(outputDir, config.collectionType, state, config.createdAfter,
					config.createdBefore);
			logVerificationResult(recheck);

			if (recheck.passed()) {
				logger.info("Verification PASSED after deduplication");
				return 0;
			}
			else {
				logger.warn("Verification FAILED (non-duplicate issues remain after deduplication)");
				return 2;
			}
		}

		logger.warn("Verification FAILED");
		return 2;
	}

	/**
	 * Derive the output directory path using the same convention as
	 * FileSystemStateRepository.
	 */
	private static Path deriveOutputDirectory(String collectionType, String repository, String state) {
		String[] repoParts = repository.split("/");
		String owner = repoParts[0];
		String repo = repoParts[1];
		String baseDir = collectionType + "/raw/" + state;
		return Paths.get(baseDir, owner, repo);
	}

	private static CollectionRequest createRequest(ParsedConfiguration config) {
		return new CollectionRequest(config.repository, config.batchSize, config.dryRun, config.incremental, config.zip,
				config.clean, config.resume, config.issueState, config.labelFilters, config.labelMode, config.maxIssues,
				config.sortBy, config.sortOrder, config.collectionType, config.prNumber, config.prState, config.verbose,
				config.createdAfter, config.createdBefore, config.singleFile, config.outputFile);
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
		logger.info("  Created after: {}", config.createdAfter != null ? config.createdAfter : "(not set)");
		logger.info("  Created before: {}", config.createdBefore != null ? config.createdBefore : "(not set)");
		logger.info("  Single file: {}", config.singleFile);
		logger.info("  Output file: {}", config.outputFile != null ? config.outputFile : "(default)");
		logger.info("  Verify: {}", config.verify);
		logger.info("  Deduplicate: {}", config.deduplicate);
		logger.info("  Verify dir: {}", config.verifyDir != null ? config.verifyDir : "(default)");
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

	private static void logVerificationResult(VerificationResult result) {
		logger.info("Verification summary:");
		logger.info("  Files scanned: {}", result.filesScanned());
		logger.info("  Total items: {}", result.totalItems());
		logger.info("  Duplicates: {}", result.duplicates().size());
		logger.info("  Date range violations: {}", result.dateRangeViolations().size());
		logger.info("  State violations: {}", result.stateViolations().size());
		logger.info("  Integrity issues: {}", result.integrityIssues().size());

		for (VerificationResult.DuplicateEntry dup : result.duplicates()) {
			logger.info("  DUP: item {} in {}", dup.itemNumber(), dup.foundInFiles());
		}
		for (VerificationResult.DateRangeViolation v : result.dateRangeViolations()) {
			logger.info("  DATE: item {} ({}): {}", v.itemNumber(), v.createdAt(), v.reason());
		}
		for (VerificationResult.StateViolation v : result.stateViolations()) {
			logger.info("  STATE: item {} expected={} actual={} in {}", v.itemNumber(), v.expectedState(),
					v.actualState(), v.fileName());
		}
		for (VerificationResult.BatchIntegrityIssue i : result.integrityIssues()) {
			logger.info("  INTEGRITY: {} — {}", i.fileName(), i.issue());
		}
	}

	private static void logDeduplicationResult(DeduplicationResult result) {
		logger.info("Deduplication summary:");
		logger.info("  Duplicates removed: {}", result.duplicatesRemoved());
		logger.info("  Files rewritten: {}", result.filesRewritten());
		logger.info("  Files deleted: {}", result.filesDeleted());
		logger.info("  Files renumbered: {}", result.filesRenumbered());
	}

}
