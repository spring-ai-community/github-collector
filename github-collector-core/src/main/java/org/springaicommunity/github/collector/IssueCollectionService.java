package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collection service for GitHub issues. Pure Java service for better testability.
 * Contains issue-specific collection logic extending BaseCollectionService.
 */
public class IssueCollectionService extends BaseCollectionService {

	private static final Logger logger = LoggerFactory.getLogger(IssueCollectionService.class);

	public IssueCollectionService(GraphQLService graphQLService, RestService restService, JsonNodeUtils jsonUtils,
			ObjectMapper objectMapper, CollectionProperties properties, CollectionStateRepository stateRepository,
			ArchiveService archiveService, BatchStrategy batchStrategy) {
		super(graphQLService, restService, jsonUtils, objectMapper, properties, stateRepository, archiveService,
				batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "issues";
	}

	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting issue collection for repository: {}", request.repository());

		// Validate and normalize request
		CollectionRequest validatedRequest = validateRequest(request);

		try {
			String[] repoParts = validatedRequest.repository().split("/");
			String owner = repoParts[0];
			String repo = repoParts[1];

			// Build search query
			String searchQuery = buildSearchQuery(owner, repo, validatedRequest);

			// Get total count
			int totalAvailableItems = getTotalItemCount(searchQuery);
			logger.info("Found {} total {} issues matching criteria", totalAvailableItems,
					validatedRequest.issueState());

			if (validatedRequest.dryRun()) {
				logger.info("DRY RUN: Would collect {} issues in batches of {}", totalAvailableItems,
						validatedRequest.batchSize());
				return new CollectionResult(totalAvailableItems, 0, "dry-run", List.of());
			}

			// Create output directory using base class method
			Path outputDir = createOutputDirectory(validatedRequest);
			cleanOutputDirectory(outputDir, validatedRequest.clean());

			// Use base class template method for batch collection
			return collectItemsInBatches(owner, repo, validatedRequest, outputDir, searchQuery, totalAvailableItems);

		}
		catch (RuntimeException e) {
			logger.error("Issue collection failed for repository: {}", validatedRequest.repository(), e);
			throw e; // Preserve runtime exceptions
		}
		catch (Exception e) {
			logger.error("Issue collection failed for repository: {}", validatedRequest.repository(), e);
			throw new RuntimeException("Issue collection failed", e);
		}
	}

	// Build GitHub search query with state and label filtering
	private String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode) {
		StringBuilder query = new StringBuilder();

		// Repository and type
		query.append("repo:").append(owner).append("/").append(repo).append(" is:issue");

		// State filter (open/closed/all)
		switch (state.toLowerCase()) {
			case "open":
				query.append(" is:open");
				break;
			case "closed":
				query.append(" is:closed");
				break;
			case "all":
				// No state filter for 'all'
				break;
			default:
				throw new IllegalArgumentException("Invalid state: " + state);
		}

		// Label filters with AND/OR logic
		if (labels != null && !labels.isEmpty()) {
			if ("all".equals(labelMode.toLowerCase())) {
				// All labels must match (AND logic) - multiple label: terms
				for (String label : labels) {
					query.append(" label:\"").append(label.trim()).append("\"");
				}
			}
			else {
				// Any label can match (OR logic) - GitHub Search API limitation
				if (labels.size() == 1) {
					query.append(" label:\"").append(labels.get(0).trim()).append("\"");
				}
				else {
					// For multiple labels with OR logic, we'll need to handle this in
					// post-processing
					// or make multiple API calls. For now, we'll use the first label and
					// warn.
					logger.warn(
							"Multiple labels with 'any' mode not fully supported in search API. Using first label: {}",
							labels.get(0));
					query.append(" label:\"").append(labels.get(0).trim()).append("\"");
				}
			}
		}

		return query.toString();
	}

	// Abstract method implementations from BaseCollectionService

	@Override
	protected int getTotalItemCount(String searchQuery) {
		return graphQLService.getSearchIssueCount(searchQuery);
	}

	@Override
	protected String buildSearchQuery(String owner, String repo, CollectionRequest request) {
		return buildSearchQuery(owner, repo, request.issueState(), request.labelFilters(), request.labelMode());
	}

	@Override
	protected List<JsonNode> fetchBatch(String searchQuery, int batchSize, String cursor) {
		// Use GraphQL for issue fetching
		JsonNode response = graphQLService.searchIssuesWithSorting(searchQuery, "updated", "desc", batchSize, cursor);
		return extractItems(response);
	}

	@Override
	protected Optional<String> extractCursor(JsonNode response) {
		// Extract cursor from GraphQL response for pagination
		return jsonUtils.getString(response, "data", "search", "pageInfo", "endCursor");
	}

	@Override
	protected List<JsonNode> extractItems(JsonNode response) {
		// Extract issues from GraphQL search response
		return new ArrayList<>(jsonUtils.getArray(response, "data", "search", "nodes"));
	}

	@Override
	protected JsonNode fetchBatchWithResponse(String searchQuery, int batchSize, String cursor) {
		return graphQLService.searchIssuesWithSorting(searchQuery, "updated", "desc", batchSize, cursor);
	}

	@Override
	protected List<JsonNode> processItemBatch(List<JsonNode> batch, String owner, String repo,
			CollectionRequest request) {
		return batch; // Issues don't need additional processing
	}

	@Override
	protected String getItemTypeName() {
		return "issues";
	}

	@Override
	protected boolean determineHasMore(List<JsonNode> batch, int requestedSize, Optional<String> nextCursor) {
		return nextCursor.isPresent() && !batch.isEmpty(); // GraphQL pagination
	}

}