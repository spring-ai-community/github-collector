package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collection service for GitHub pull requests with soft approval detection. Extends
 * BaseCollectionService with PR-specific functionality.
 */

public class PRCollectionService extends BaseCollectionService {

	private static final Logger logger = LoggerFactory.getLogger(PRCollectionService.class);

	public PRCollectionService(GraphQLService graphQLService, RestService restService, JsonNodeUtils jsonUtils,
			ObjectMapper objectMapper, CollectionProperties properties, CollectionStateRepository stateRepository,
			ArchiveService archiveService, BatchStrategy batchStrategy) {
		super(graphQLService, restService, jsonUtils, objectMapper, properties, stateRepository, archiveService,
				batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "prs";
	}

	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting PR collection for repository: {}", request.repository());

		// Validate and normalize request
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
			// Create output directory
			Path outputDir = createOutputDirectory(request);
			cleanOutputDirectory(outputDir, request.clean());

			// Get PR data
			JsonNode prData = restService.getPullRequest(owner, repo, request.prNumber());
			logger.info("PR #{} found: {}", request.prNumber(), jsonUtils.getString(prData, "title").orElse("Unknown"));

			// Get PR reviews for soft approval detection
			JsonNode reviewsData = restService.getPullRequestReviews(owner, repo, request.prNumber());
			logger.info("Found {} reviews for PR #{}", reviewsData.size(), request.prNumber());

			// Enhance PR data with soft approval detection
			JsonNode enhancedPR = enhancePRWithSoftApproval(prData, reviewsData);

			// Save PR data
			List<JsonNode> prList = List.of(enhancedPR);
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
	 * Collect multiple PRs using search - using proven issue collection pattern
	 */
	private CollectionResult collectMultiplePRs(String owner, String repo, CollectionRequest request) {
		logger.info("Collecting {} PRs from {}/{}", request.prState(), owner, repo);

		// Build search query
		String searchQuery = buildSearchQuery(owner, repo, request);

		// Get total count
		int totalAvailableItems = getTotalItemCount(searchQuery);
		logger.info("Found {} total {} PRs matching criteria", totalAvailableItems, request.prState());

		if (request.dryRun()) {
			logger.info("DRY RUN: Would collect {} PRs", totalAvailableItems);
			return new CollectionResult(totalAvailableItems, 0, "dry-run", List.of("pr_batch.json"));
		}

		// Create output directory
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
	protected List<JsonNode> fetchBatch(String searchQuery, int batchSize, String cursor) {
		// For now, use REST API search (could be enhanced with GraphQL later)
		JsonNode searchResults = restService.searchPRs(searchQuery, batchSize, cursor);
		return extractItems(searchResults);
	}

	@Override
	protected JsonNode fetchBatchWithResponse(String searchQuery, int batchSize, String cursor) {
		return restService.searchPRs(searchQuery, batchSize, cursor);
	}

	@Override
	protected List<JsonNode> processItemBatch(List<JsonNode> batch, String owner, String repo,
			CollectionRequest request) {
		return enhancePRsWithSoftApproval(batch, owner, repo, request.verbose());
	}

	@Override
	protected String getItemTypeName() {
		return "PRs";
	}

	@Override
	protected boolean determineHasMore(List<JsonNode> batch, int requestedSize, Optional<String> nextCursor) {
		// For REST API pagination: if we got fewer items than requested, we're done
		return batch.size() == requestedSize;
	}

	@Override
	protected Optional<String> extractCursor(JsonNode response) {
		// For REST API pagination, check if we have more pages
		try {
			JsonNode items = response.path("items");
			if (!items.isArray() || items.size() == 0) {
				return Optional.empty(); // No more pages
			}

			// GitHub search API returns information about pagination
			// If items.size() is less than per_page, we're on the last page
			// Since we don't know the exact per_page used, check if we got fewer than
			// expected

			// Conservative approach: if we got fewer than 30 items (GitHub's default
			// per_page),
			// assume we're on the last page
			if (items.size() < 30) {
				return Optional.empty();
			}

			// More items available, continue pagination
			return Optional.of("next");
		}
		catch (Exception e) {
			logger.warn("Failed to extract cursor from PR response: {}", e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	protected List<JsonNode> extractItems(JsonNode response) {
		// Extract PRs from search response
		return jsonUtils.getArray(response, "items");
	}

	/**
	 * Enhance a single PR with soft approval detection
	 */
	private JsonNode enhancePRWithSoftApproval(JsonNode prData, JsonNode reviewsData) {
		boolean hasSoftApproval = detectSoftApproval(reviewsData);

		// Create enhanced PR data with soft approval information
		Map<String, Object> enhancedPR = objectMapper.convertValue(prData, Map.class);
		enhancedPR.put("soft_approval_detected", hasSoftApproval);
		enhancedPR.put("analysis_timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

		if (hasSoftApproval) {
			enhancedPR.put("soft_approval_details", extractSoftApprovalDetails(reviewsData));
		}

		return objectMapper.valueToTree(enhancedPR);
	}

	/**
	 * Enhance multiple PRs with soft approval detection
	 */
	private List<JsonNode> enhancePRsWithSoftApproval(List<JsonNode> prs, String owner, String repo, boolean verbose) {
		List<JsonNode> enhancedPRs = new ArrayList<>();
		int total = prs.size();

		if (verbose) {
			logger.info("🔍 Analyzing {} PRs for soft approval detection...", total);
		}

		for (int i = 0; i < prs.size(); i++) {
			JsonNode pr = prs.get(i);
			try {
				int prNumber = jsonUtils.getInt(pr, "number").orElse(-1);
				if (prNumber > 0) {
					if (verbose) {
						String prTitle = jsonUtils.getString(pr, "title").orElse("Unknown");
						logger.info("  📋 Processing PR #{} ({}/{}) - {}", prNumber, i + 1, total,
								prTitle.length() > 60 ? prTitle.substring(0, 60) + "..." : prTitle);
					}

					// Get reviews for this PR
					JsonNode reviewsData = restService.getPullRequestReviews(owner, repo, prNumber);
					JsonNode enhancedPR = enhancePRWithSoftApproval(pr, reviewsData);
					enhancedPRs.add(enhancedPR);

					// Check if soft approval was detected
					if (verbose && enhancedPR.path("soft_approval_detected").asBoolean()) {
						logger.info("  ✨ Soft approval detected for PR #{}", prNumber);
					}
				}
				else {
					// Add PR without enhancement if number is missing
					enhancedPRs.add(pr);
				}
			}
			catch (Exception e) {
				logger.warn("Failed to enhance PR with soft approval detection: {}", e.getMessage());
				enhancedPRs.add(pr); // Add original PR without enhancement
			}
		}

		if (verbose) {
			long softApprovalCount = enhancedPRs.stream()
				.mapToLong(pr -> pr.path("soft_approval_detected").asBoolean() ? 1 : 0)
				.sum();
			logger.info("✅ Completed soft approval analysis: {}/{} PRs have soft approvals", softApprovalCount, total);
		}

		return enhancedPRs;
	}

	/**
	 * Detect soft approval in PR reviews Soft approval = approval from non-member
	 * (CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR)
	 */
	private boolean detectSoftApproval(JsonNode reviewsData) {
		if (!reviewsData.isArray()) {
			return false;
		}

		for (JsonNode review : reviewsData) {
			String state = jsonUtils.getString(review, "state").orElse("");
			String authorAssociation = jsonUtils.getString(review, "author_association").orElse("");
			String authorLogin = jsonUtils.getString(review, "user", "login").orElse("");

			if ("APPROVED".equals(state) && ("CONTRIBUTOR".equals(authorAssociation)
					|| "FIRST_TIME_CONTRIBUTOR".equals(authorAssociation))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extract soft approval details for enhanced PR data
	 */
	private Map<String, Object> extractSoftApprovalDetails(JsonNode reviewsData) {
		Map<String, Object> details = new HashMap<>();
		List<Map<String, String>> softApprovals = new ArrayList<>();

		if (reviewsData.isArray()) {
			for (JsonNode review : reviewsData) {
				String state = jsonUtils.getString(review, "state").orElse("");
				String authorAssociation = jsonUtils.getString(review, "author_association").orElse("");
				String authorLogin = jsonUtils.getString(review, "user", "login").orElse("");

				if ("APPROVED".equals(state) && ("CONTRIBUTOR".equals(authorAssociation)
						|| "FIRST_TIME_CONTRIBUTOR".equals(authorAssociation))) {

					Map<String, String> approval = new HashMap<>();
					approval.put("reviewer", authorLogin);
					approval.put("association", authorAssociation);
					approval.put("submitted_at", jsonUtils.getString(review, "submitted_at").orElse(""));
					softApprovals.add(approval);
				}
			}
		}

		details.put("soft_approvals", softApprovals);
		details.put("count", softApprovals.size());
		return details;
	}

}