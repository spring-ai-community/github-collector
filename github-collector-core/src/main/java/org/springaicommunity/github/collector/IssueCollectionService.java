package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
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
		return buildSearchQuery(owner, repo, request.issueState(), request.labelFilters(), request.labelMode(),
				request.createdAfter(), request.createdBefore());
	}

	@Override
	protected SearchResult<Issue> fetchBatch(String searchQuery, int batchSize, @Nullable String cursor) {
		return graphQLService.searchIssues(searchQuery, "updated", "desc", batchSize, cursor);
	}

	@Override
	protected List<Issue> processItemBatch(List<Issue> batch, String owner, String repo, CollectionRequest request) {
		return enhanceIssuesWithEvents(batch, owner, repo, request.verbose());
	}

	/**
	 * Enhance issues with timeline events (label changes, state changes, etc.).
	 *
	 * <p>
	 * Fetches events for each issue via the REST API and creates new Issue records with
	 * the events populated. This is essential for tracking label authority (who applied
	 * labels) and label stability (label churn after issue closure).
	 */
	private List<Issue> enhanceIssuesWithEvents(List<Issue> issues, String owner, String repo, boolean verbose) {
		List<Issue> enhancedIssues = new ArrayList<>();
		int total = issues.size();

		if (verbose) {
			logger.info("Fetching events for {} issues...", total);
		}

		for (int i = 0; i < issues.size(); i++) {
			Issue issue = issues.get(i);
			try {
				int issueNumber = issue.number();
				if (issueNumber > 0) {
					if (verbose) {
						String issueTitle = issue.title();
						logger.info("  Fetching events for issue #{} ({}/{}) - {}", issueNumber, i + 1, total,
								issueTitle.length() > 60 ? issueTitle.substring(0, 60) + "..." : issueTitle);
					}

					// Get events for this issue
					List<IssueEvent> events = restService.getIssueEvents(owner, repo, issueNumber);

					// Create enhanced issue with events
					Issue enhancedIssue = new Issue(issue.number(), issue.title(), issue.body(), issue.state(),
							issue.createdAt(), issue.updatedAt(), issue.closedAt(), issue.url(), issue.author(),
							issue.comments(), issue.labels(), events);

					enhancedIssues.add(enhancedIssue);

					if (verbose) {
						long labelEventCount = events.stream()
							.filter(e -> "labeled".equals(e.event()) || "unlabeled".equals(e.event()))
							.count();
						if (labelEventCount > 0) {
							logger.info("    Found {} label events", labelEventCount);
						}
					}
				}
				else {
					enhancedIssues.add(issue);
				}
			}
			catch (Exception e) {
				logger.warn("Failed to fetch events for issue #{}: {}", issue.number(), e.getMessage());
				enhancedIssues.add(issue);
			}
		}

		if (verbose) {
			long totalLabelEvents = enhancedIssues.stream()
				.flatMap(i -> i.events().stream())
				.filter(e -> "labeled".equals(e.event()) || "unlabeled".equals(e.event()))
				.count();
			logger.info("Completed event fetching: {} total label events across {} issues", totalLabelEvents, total);
		}

		return enhancedIssues;
	}

	@Override
	protected String getItemTypeName() {
		return "issues";
	}

	// Build GitHub search query with state, label, and date filtering
	private String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode,
			String createdAfter, String createdBefore) {
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

		// Date range filtering via GitHub search qualifiers
		if (createdAfter != null && createdBefore != null) {
			query.append(" created:").append(createdAfter).append("..").append(createdBefore);
		}
		else if (createdAfter != null) {
			query.append(" created:>=").append(createdAfter);
		}
		else if (createdBefore != null) {
			query.append(" created:<").append(createdBefore);
		}

		return query.toString();
	}

}
