package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Collection service for GitHub pull requests with soft approval detection.
 *
 * <p>
 * Works with strongly-typed {@link AnalyzedPullRequest} records that include soft
 * approval analysis results.
 */
public class PRCollectionService extends BaseCollectionService<AnalyzedPullRequest> {

	private static final Logger logger = LoggerFactory.getLogger(PRCollectionService.class);

	public PRCollectionService(GraphQLService graphQLService, RestService restService, ObjectMapper objectMapper,
			CollectionProperties properties, CollectionStateRepository stateRepository, ArchiveService archiveService,
			BatchStrategy<AnalyzedPullRequest> batchStrategy) {
		super(graphQLService, restService, objectMapper, properties, stateRepository, archiveService, batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "prs";
	}

	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting PR collection for repository: {}", request.repository());

		CollectionRequest validatedRequest = validateRequest(request);

		try {
			String[] repoParts = validatedRequest.repository().split("/");
			String owner = repoParts[0];
			String repo = repoParts[1];

			// Handle specific PR collection
			if (validatedRequest.prNumber() != null) {
				return collectSpecificPR(owner, repo, validatedRequest);
			}

			// Handle multiple PR collection
			return collectMultiplePRs(owner, repo, validatedRequest);

		}
		catch (Exception e) {
			logger.error("PR collection failed for repository: {}", validatedRequest.repository(), e);
			throw e;
		}
	}

	/**
	 * Collect a specific PR by number
	 */
	private CollectionResult collectSpecificPR(String owner, String repo, CollectionRequest request) {
		logger.info("Collecting PR #{} from {}/{}", request.prNumber(), owner, repo);

		if (request.dryRun()) {
			logger.info("DRY RUN: Would collect PR #{}", request.prNumber());
			return new CollectionResult(1, 1, "dry-run", List.of("pr_" + request.prNumber() + ".json"));
		}

		try {
			Path outputDir = createOutputDirectory(request);
			cleanOutputDirectory(outputDir, request.clean());

			// Get PR data
			PullRequest prData = restService.getPullRequest(owner, repo, request.prNumber());
			logger.info("PR #{} found: {}", request.prNumber(), prData.title());

			// Get PR reviews for soft approval detection
			List<Review> reviews = restService.getPullRequestReviews(owner, repo, request.prNumber());
			logger.info("Found {} reviews for PR #{}", reviews.size(), request.prNumber());

			// Analyze PR for soft approval
			AnalyzedPullRequest analyzedPR = analyzePullRequest(prData, reviews);

			// Save PR data
			List<AnalyzedPullRequest> prList = List.of(analyzedPR);
			String filename = saveBatchToFile(outputDir, 1, prList, request);

			// Create ZIP if requested
			List<String> batchFiles = List.of(filename);
			createZipFile(outputDir, batchFiles, request);

			logger.info("PR #{} collection completed successfully", request.prNumber());
			return new CollectionResult(1, 1, outputDir.toString(), batchFiles);

		}
		catch (Exception e) {
			logger.error("Failed to collect PR #{}: {}", request.prNumber(), e.getMessage());
			throw new RuntimeException("Failed to collect PR #" + request.prNumber(), e);
		}
	}

	/**
	 * Collect multiple PRs using search
	 */
	private CollectionResult collectMultiplePRs(String owner, String repo, CollectionRequest request) {
		logger.info("Collecting {} PRs from {}/{}", request.prState(), owner, repo);

		String searchQuery = buildSearchQuery(owner, repo, request);
		int totalAvailableItems = getTotalItemCount(searchQuery);
		logger.info("Found {} total {} PRs matching criteria", totalAvailableItems, request.prState());

		if (request.dryRun()) {
			logger.info("DRY RUN: Would collect {} PRs", totalAvailableItems);
			return new CollectionResult(totalAvailableItems, 0, "dry-run", List.of("pr_batch.json"));
		}

		Path outputDir = createOutputDirectory(request);
		cleanOutputDirectory(outputDir, request.clean());

		try {
			return collectItemsInBatches(owner, repo, request, outputDir, searchQuery, totalAvailableItems);
		}
		catch (Exception e) {
			logger.error("Multiple PR collection failed: {}", e.getMessage());
			throw new RuntimeException("Multiple PR collection failed", e);
		}
	}

	@Override
	protected int getTotalItemCount(String searchQuery) {
		return restService.getTotalPRCount(searchQuery);
	}

	@Override
	protected String buildSearchQuery(String owner, String repo, CollectionRequest request) {
		return restService.buildPRSearchQuery(request.repository(), request.prState(), request.labelFilters(),
				request.labelMode());
	}

	@Override
	protected SearchResult<AnalyzedPullRequest> fetchBatch(String searchQuery, int batchSize, String cursor) {
		// Fetch PRs from REST API
		SearchResult<PullRequest> prResult = restService.searchPRs(searchQuery, batchSize, cursor);

		// Convert to AnalyzedPullRequest (without reviews for now - they're added in
		// processItemBatch)
		List<AnalyzedPullRequest> analyzedPRs = prResult.items()
			.stream()
			.map(pr -> AnalyzedPullRequest.from(pr, false, null, List.of()))
			.toList();

		return new SearchResult<>(analyzedPRs, prResult.nextCursor(), prResult.hasMore());
	}

	@Override
	protected List<AnalyzedPullRequest> processItemBatch(List<AnalyzedPullRequest> batch, String owner, String repo,
			CollectionRequest request) {
		return enhancePRsWithSoftApproval(batch, owner, repo, request.verbose());
	}

	@Override
	protected String getItemTypeName() {
		return "PRs";
	}

	/**
	 * Analyze a single PR for soft approval
	 */
	private AnalyzedPullRequest analyzePullRequest(PullRequest pr, List<Review> reviews) {
		boolean hasSoftApproval = detectSoftApproval(reviews);
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		List<SoftApproval> softApprovals = hasSoftApproval ? extractSoftApprovals(reviews) : List.of();

		// Create PR with reviews included
		PullRequest prWithReviews = new PullRequest(pr.number(), pr.title(), pr.body(), pr.state(), pr.createdAt(),
				pr.updatedAt(), pr.closedAt(), pr.mergedAt(), pr.url(), pr.htmlUrl(), pr.author(), pr.comments(),
				pr.labels(), reviews, pr.draft(), pr.merged(), pr.mergeCommitSha(), pr.headRef(), pr.baseRef(),
				pr.additions(), pr.deletions(), pr.changedFiles());

		return AnalyzedPullRequest.from(prWithReviews, hasSoftApproval, timestamp, softApprovals);
	}

	/**
	 * Enhance multiple PRs with soft approval detection
	 */
	private List<AnalyzedPullRequest> enhancePRsWithSoftApproval(List<AnalyzedPullRequest> prs, String owner,
			String repo, boolean verbose) {
		List<AnalyzedPullRequest> enhancedPRs = new ArrayList<>();
		int total = prs.size();

		if (verbose) {
			logger.info("Analyzing {} PRs for soft approval detection...", total);
		}

		for (int i = 0; i < prs.size(); i++) {
			AnalyzedPullRequest pr = prs.get(i);
			try {
				int prNumber = pr.number();
				if (prNumber > 0) {
					if (verbose) {
						String prTitle = pr.title();
						logger.info("  Processing PR #{} ({}/{}) - {}", prNumber, i + 1, total,
								prTitle.length() > 60 ? prTitle.substring(0, 60) + "..." : prTitle);
					}

					// Get reviews for this PR
					List<Review> reviews = restService.getPullRequestReviews(owner, repo, prNumber);

					// Analyze for soft approval
					boolean hasSoftApproval = detectSoftApproval(reviews);
					String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
					List<SoftApproval> softApprovals = hasSoftApproval ? extractSoftApprovals(reviews) : List.of();

					// Create analyzed PR with reviews
					AnalyzedPullRequest analyzedPR = new AnalyzedPullRequest(pr.number(), pr.title(), pr.body(),
							pr.state(), pr.createdAt(), pr.updatedAt(), pr.closedAt(), pr.mergedAt(), pr.url(),
							pr.htmlUrl(), pr.author(), pr.comments(), pr.labels(), reviews, pr.draft(), pr.merged(),
							pr.mergeCommitSha(), pr.headRef(), pr.baseRef(), pr.additions(), pr.deletions(),
							pr.changedFiles(), hasSoftApproval, timestamp, softApprovals);

					enhancedPRs.add(analyzedPR);

					if (verbose && hasSoftApproval) {
						logger.info("  Soft approval detected for PR #{}", prNumber);
					}
				}
				else {
					enhancedPRs.add(pr);
				}
			}
			catch (Exception e) {
				logger.warn("Failed to enhance PR with soft approval detection: {}", e.getMessage());
				enhancedPRs.add(pr);
			}
		}

		if (verbose) {
			long softApprovalCount = enhancedPRs.stream().filter(AnalyzedPullRequest::softApprovalDetected).count();
			logger.info("Completed soft approval analysis: {}/{} PRs have soft approvals", softApprovalCount, total);
		}

		return enhancedPRs;
	}

	/**
	 * Detect soft approval in PR reviews. Soft approval = approval from non-member
	 * (CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR)
	 */
	private boolean detectSoftApproval(List<Review> reviews) {
		for (Review review : reviews) {
			if ("APPROVED".equals(review.state()) && ("CONTRIBUTOR".equals(review.authorAssociation())
					|| "FIRST_TIME_CONTRIBUTOR".equals(review.authorAssociation()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extract soft approval details
	 */
	private List<SoftApproval> extractSoftApprovals(List<Review> reviews) {
		List<SoftApproval> softApprovals = new ArrayList<>();
		for (Review review : reviews) {
			if ("APPROVED".equals(review.state()) && ("CONTRIBUTOR".equals(review.authorAssociation())
					|| "FIRST_TIME_CONTRIBUTOR".equals(review.authorAssociation()))) {
				String submittedAt = review.submittedAt() != null
						? review.submittedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
				softApprovals.add(new SoftApproval(review.author().login(), review.authorAssociation(), submittedAt));
			}
		}
		return softApprovals;
	}

}
