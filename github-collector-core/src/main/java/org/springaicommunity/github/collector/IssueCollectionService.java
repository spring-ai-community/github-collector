package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Collection service for GitHub issues.
 *
 * <p>
 * Works with strongly-typed {@link Issue} records instead of raw JSON.
 */
public class IssueCollectionService extends BaseCollectionService<Issue> {

	private static final Logger logger = LoggerFactory.getLogger(IssueCollectionService.class);

	public IssueCollectionService(GraphQLService graphQLService, RestService restService, ObjectMapper objectMapper,
			CollectionProperties properties, CollectionStateRepository stateRepository, ArchiveService archiveService,
			BatchStrategy<Issue> batchStrategy) {
		super(graphQLService, restService, objectMapper, properties, stateRepository, archiveService, batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "issues";
	}

	/**
	 * Collects GitHub issues based on the provided request parameters.
	 *
	 * <p>
	 * This method fetches issues matching the specified criteria (state, labels) and
	 * saves them in batches to the output directory. The collection process supports
	 * pagination, rate limiting, and resumption from previous runs.
	 * @param request the collection request containing repository, filters, and options
	 * @return the collection result with counts and output file information
	 * @throws RuntimeException if the collection fails due to API errors or I/O issues
	 */
	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting issue collection for repository: {}", request.repository());

		CollectionRequest validatedRequest = validateRequest(request);

		try {
			String[] repoParts = validatedRequest.repository().split("/");
			String owner = repoParts[0];
			String repo = repoParts[1];

			String searchQuery = buildSearchQuery(owner, repo, validatedRequest);

			int totalAvailableItems = getTotalItemCount(searchQuery);
			logger.info("Found {} total {} issues matching criteria", totalAvailableItems,
					validatedRequest.issueState());

			if (validatedRequest.dryRun()) {
				logger.info("DRY RUN: Would collect {} issues in batches of {}", totalAvailableItems,
						validatedRequest.batchSize());
				return new CollectionResult(totalAvailableItems, 0, "dry-run", List.of());
			}

			Path outputDir = createOutputDirectory(validatedRequest);
			cleanOutputDirectory(outputDir, validatedRequest.clean());

			return collectItemsInBatches(owner, repo, validatedRequest, outputDir, searchQuery, totalAvailableItems);

		}
		catch (RuntimeException e) {
			logger.error("Issue collection failed for repository: {}", validatedRequest.repository(), e);
			throw e;
		}
		catch (Exception e) {
			logger.error("Issue collection failed for repository: {}", validatedRequest.repository(), e);
			throw new RuntimeException("Issue collection failed", e);
		}
	}

	@Override
	protected int getTotalItemCount(String searchQuery) {
		return graphQLService.getSearchIssueCount(searchQuery);
	}

	@Override
	protected String buildSearchQuery(String owner, String repo, CollectionRequest request) {
		return buildSearchQuery(owner, repo, request.issueState(), request.labelFilters(), request.labelMode());
	}

	@Override
	protected SearchResult<Issue> fetchBatch(String searchQuery, int batchSize, @Nullable String cursor) {
		return graphQLService.searchIssues(searchQuery, "updated", "desc", batchSize, cursor);
	}

	@Override
	protected List<Issue> processItemBatch(List<Issue> batch, String owner, String repo, CollectionRequest request) {
		// Issues don't need additional processing
		return batch;
	}

	@Override
	protected String getItemTypeName() {
		return "issues";
	}

	// Build GitHub search query with state and label filtering
	private String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode) {
		StringBuilder query = new StringBuilder();

		query.append("repo:").append(owner).append("/").append(repo).append(" is:issue");

		switch (state.toLowerCase()) {
			case "open":
				query.append(" is:open");
				break;
			case "closed":
				query.append(" is:closed");
				break;
			case "all":
				break;
			default:
				throw new IllegalArgumentException("Invalid state: " + state);
		}

		if (labels != null && !labels.isEmpty()) {
			if ("all".equals(labelMode.toLowerCase())) {
				for (String label : labels) {
					query.append(" label:\"").append(label.trim()).append("\"");
				}
			}
			else {
				if (labels.size() == 1) {
					query.append(" label:\"").append(labels.get(0).trim()).append("\"");
				}
				else {
					logger.warn(
							"Multiple labels with 'any' mode not fully supported in search API. Using first label: {}",
							labels.get(0));
					query.append(" label:\"").append(labels.get(0).trim()).append("\"");
				}
			}
		}

		return query.toString();
	}

}
