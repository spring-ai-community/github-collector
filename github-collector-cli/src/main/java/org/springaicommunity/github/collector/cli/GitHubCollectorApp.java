package org.springaicommunity.github.collector.cli;

import org.springaicommunity.github.collector.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * GitHub Collector CLI Application
 *
 * Spring Boot command-line application to collect GitHub issues and PRs from a repository
 * using GraphQL and REST APIs with library components.
 *
 * Usage: java -jar github-collector-cli.jar [OPTIONS]
 *
 * Environment Variables: GITHUB_TOKEN - GitHub personal access token for authentication
 *
 * Examples: java -jar github-collector-cli.jar --repo spring-projects/spring-ai java -jar
 * github-collector-cli.jar --batch-size 50 --incremental java -jar
 * github-collector-cli.jar --dry-run --verbose
 */
@SpringBootApplication
@ComponentScan(
		basePackages = { "org.springaicommunity.github.collector", "org.springaicommunity.github.collector.cli" })
public class GitHubCollectorApp implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(GitHubCollectorApp.class);

	private final GitHubGraphQLService graphQLService;

	private final GitHubRestService restService;

	private final IssueCollectionService issueCollectionService;

	private final PRCollectionService prCollectionService;

	private final ArgumentParser argumentParser;

	// Collection configuration - will be set from parsed arguments
	private String repo;

	private int batchSize;

	private boolean dryRun = false;

	private boolean incremental = false;

	private boolean zip = false;

	private boolean verbose = false;

	private boolean clean = true;

	private boolean resume = false;

	private String issueState;

	private List<String> labelFilters = new ArrayList<>();

	private String labelMode;

	// Dashboard enhancement parameters
	private Integer maxIssues;

	private String sortBy;

	private String sortOrder;

	// PR collection parameters
	private String collectionType;

	private Integer prNumber;

	private String prState;

	public GitHubCollectorApp(GitHubGraphQLService graphQLService, GitHubRestService restService,
			IssueCollectionService issueCollectionService, PRCollectionService prCollectionService,
			ArgumentParser argumentParser) {
		this.graphQLService = graphQLService;
		this.restService = restService;
		this.issueCollectionService = issueCollectionService;
		this.prCollectionService = prCollectionService;
		this.argumentParser = argumentParser;
	}

	public static void main(String[] args) {
		// Configure Spring Boot to run as console application
		SpringApplication app = new SpringApplication(GitHubCollectorApp.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		// Check for help request first
		if (argumentParser.isHelpRequested(args)) {
			System.out.println(argumentParser.generateHelpText());
			return;
		}

		// Parse and validate arguments
		ParsedConfiguration config = argumentParser.parseAndValidate(args);
		argumentParser.validateEnvironment();

		// Apply parsed configuration to instance variables
		applyParsedConfiguration(config);

		logger.info("Configuration:");
		logger.info("  Repository: {}", repo);
		logger.info("  Batch size: {}", batchSize);
		logger.info("  Dry run: {}", dryRun);
		logger.info("  Incremental: {}", incremental);
		logger.info("  Zip: {}", zip);
		logger.info("  Verbose: {}", verbose);
		logger.info("  Clean: {}", clean);
		logger.info("  Resume: {}", resume);
		logger.info("  Issue state: {}", issueState);
		logger.info("  Label filters: {}", labelFilters);
		logger.info("  Label mode: {}", labelMode);
		logger.info("  Max issues: {}", maxIssues != null ? maxIssues : "unlimited");
		logger.info("  Sort by: {}", sortBy);
		logger.info("  Sort order: {}", sortOrder);
		logger.info("  Collection type: {}", collectionType);
		logger.info("  PR number: {}", prNumber != null ? prNumber : "all");
		logger.info("  PR state: {}", prState);

		try {
			// Create collection request with dashboard and PR enhancements
			CollectionRequest request = new CollectionRequest(repo, batchSize, dryRun, incremental, zip, clean, resume,
					issueState, labelFilters, labelMode, maxIssues, sortBy, sortOrder, collectionType, prNumber,
					prState, verbose);

			// Execute collection based on type
			CollectionResult result;
			if ("prs".equals(collectionType)) {
				result = prCollectionService.collectItems(request);
			}
			else {
				result = issueCollectionService.collectIssues(request);
			}

			// Log results
			logger.info("Collection completed successfully!");
			logger.info("Total issues: {}", result.totalIssues());
			logger.info("Processed issues: {}", result.processedIssues());
			logger.info("Output directory: {}", result.outputDirectory());
			logger.info("Batch files created: {}", result.batchFiles().size());

			if (verbose && !result.batchFiles().isEmpty()) {
				logger.info("Batch files:");
				for (String batchFile : result.batchFiles()) {
					logger.info("  - {}", batchFile);
				}
			}

		}
		catch (Exception e) {
			logger.error("Collection failed: {}", e.getMessage());
			if (verbose) {
				logger.error("Stack trace:", e);
			}
			System.exit(1);
		}
	}

	private void applyParsedConfiguration(ParsedConfiguration config) {
		this.repo = config.repository;
		this.batchSize = config.batchSize;
		this.dryRun = config.dryRun;
		this.incremental = config.incremental;
		this.zip = config.zip;
		this.verbose = config.verbose;
		this.clean = config.clean;
		this.resume = config.resume;
		this.issueState = config.issueState;
		this.labelFilters = config.labelFilters;
		this.labelMode = config.labelMode;
		this.maxIssues = config.maxIssues;
		this.sortBy = config.sortBy;
		this.sortOrder = config.sortOrder;
		this.collectionType = config.collectionType;
		this.prNumber = config.prNumber;
		this.prState = config.prState;
	}

}