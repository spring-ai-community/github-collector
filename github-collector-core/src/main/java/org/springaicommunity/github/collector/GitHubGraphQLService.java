package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for GitHub GraphQL API operations.
 *
 * <p>
 * Converts GitHub API JSON responses to strongly-typed DTOs at the service boundary,
 * encapsulating all JSON parsing logic here.
 */
public class GitHubGraphQLService implements GraphQLService {

	private static final Logger logger = LoggerFactory.getLogger(GitHubGraphQLService.class);

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

	private final GitHubClient httpClient;

	private final ObjectMapper objectMapper;

	public GitHubGraphQLService(GitHubClient httpClient, ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public int getTotalIssueCount(String owner, String repo, String state) {
		String query = """
				query($owner: String!, $repo: String!, $states: [IssueState!]!) {
				    repository(owner: $owner, name: $repo) {
				        issues(states: $states) {
				            totalCount
				        }
				    }
				}
				""";

		Object variables = Map.of("owner", owner, "repo", repo, "states", List.of(state.toUpperCase()));

		JsonNode result = executeGraphQL(query, variables);
		return result.path("data").path("repository").path("issues").path("totalCount").asInt(0);
	}

	@Override
	public int getSearchIssueCount(String searchQuery) {
		String query = """
				query($query: String!) {
				    search(query: $query, type: ISSUE, first: 1) {
				        issueCount
				    }
				}
				""";

		Object variables = Map.of("query", searchQuery);

		JsonNode result = executeGraphQL(query, variables);
		return result.path("data").path("search").path("issueCount").asInt(0);
	}

	@Override
	public SearchResult<Issue> searchIssues(String searchQuery, String sortBy, String sortOrder, int first,
			@Nullable String after) {
		String sortParam = convertSortToGraphQL(sortBy);
		String orderParam = convertOrderToGraphQL(sortOrder);

		String query = """
				query($query: String!, $first: Int!, $after: String) {
				    search(query: $query, type: ISSUE, first: $first, after: $after) {
				        pageInfo {
				            hasNextPage
				            endCursor
				        }
				        issueCount
				        nodes {
				            ... on Issue {
				                number
				                title
				                body
				                state
				                createdAt
				                updatedAt
				                closedAt
				                url
				                author {
				                    login
				                    ... on User {
				                        name
				                    }
				                }
				                labels(first: 20) {
				                    nodes {
				                        name
				                        color
				                        description
				                    }
				                }
				                comments(first: 100) {
				                    nodes {
				                        author {
				                            login
				                            ... on User {
				                                name
				                            }
				                        }
				                        body
				                        createdAt
				                    }
				                }
				            }
				        }
				    }
				}
				""";

		Object variables = Map.of("query", buildSortedSearchQuery(searchQuery, sortParam, orderParam), "first", first,
				"after", after != null ? after : "");

		JsonNode response = executeGraphQL(query, variables);

		// Parse pagination info
		JsonNode pageInfo = response.path("data").path("search").path("pageInfo");
		boolean hasMore = pageInfo.path("hasNextPage").asBoolean(false);
		String nextCursor = hasMore ? pageInfo.path("endCursor").asText(null) : null;

		// Parse issues from nodes
		List<Issue> issues = new ArrayList<>();
		JsonNode nodes = response.path("data").path("search").path("nodes");
		if (nodes.isArray()) {
			for (JsonNode node : nodes) {
				Issue issue = parseIssue(node);
				if (issue != null) {
					issues.add(issue);
				}
			}
		}

		return new SearchResult<>(issues, nextCursor, hasMore);
	}

	// ========== JSON Parsing Methods (at service boundary) ==========

	private @Nullable Issue parseIssue(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			// Events are not available via GraphQL - they will be fetched via REST API
			// in IssueCollectionService.processItemBatch()
			return new Issue(node.path("number").asInt(), node.path("title").asText(""), node.path("body").asText(null),
					node.path("state").asText(""), parseDateTime(node.path("createdAt").asText(null)),
					parseDateTime(node.path("updatedAt").asText(null)),
					parseDateTime(node.path("closedAt").asText(null)), node.path("url").asText(""),
					parseAuthor(node.path("author")), parseComments(node.path("comments").path("nodes")),
					parseLabels(node.path("labels").path("nodes")), List.of());
		}
		catch (Exception e) {
			logger.warn("Failed to parse issue: {}", e.getMessage());
			return null;
		}
	}

	private Author parseAuthor(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return new Author("unknown", null);
		}
		return new Author(node.path("login").asText("unknown"), node.path("name").asText(null));
	}

	private List<Comment> parseComments(JsonNode nodes) {
		List<Comment> comments = new ArrayList<>();
		if (nodes != null && nodes.isArray()) {
			for (JsonNode node : nodes) {
				comments.add(new Comment(parseAuthor(node.path("author")), node.path("body").asText(""),
						parseDateTime(node.path("createdAt").asText(null))));
			}
		}
		return comments;
	}

	private List<Label> parseLabels(JsonNode nodes) {
		List<Label> labels = new ArrayList<>();
		if (nodes != null && nodes.isArray()) {
			for (JsonNode node : nodes) {
				labels.add(new Label(node.path("name").asText(""), node.path("color").asText(null),
						node.path("description").asText(null)));
			}
		}
		return labels;
	}

	private @Nullable LocalDateTime parseDateTime(@Nullable String dateTimeStr) {
		if (dateTimeStr == null || dateTimeStr.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
		}
		catch (DateTimeParseException e) {
			logger.warn("Failed to parse datetime: {}", dateTimeStr);
			return null;
		}
	}

	// ========== Internal GraphQL Execution ==========

	private JsonNode executeGraphQL(String query, Object variables) {
		try {
			String requestBody = objectMapper
				.writeValueAsString(Map.of("query", query, "variables", variables != null ? variables : Map.of()));

			String response = httpClient.postGraphQL(requestBody);

			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("GraphQL query failed: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

	private String buildSortedSearchQuery(String baseQuery, String sortBy, String sortOrder) {
		if (sortBy != null && !"updated".equals(sortBy)) {
			return baseQuery + " sort:" + sortBy + "-" + sortOrder;
		}
		return baseQuery;
	}

	private String convertSortToGraphQL(String sortBy) {
		if (sortBy == null)
			return "updated";

		return switch (sortBy.toLowerCase()) {
			case "updated" -> "updated";
			case "created" -> "created";
			case "comments" -> "comments";
			case "reactions" -> "reactions";
			default -> "updated";
		};
	}

	private String convertOrderToGraphQL(String sortOrder) {
		if (sortOrder == null)
			return "desc";

		return switch (sortOrder.toLowerCase()) {
			case "desc" -> "desc";
			case "asc" -> "asc";
			default -> "desc";
		};
	}

}
