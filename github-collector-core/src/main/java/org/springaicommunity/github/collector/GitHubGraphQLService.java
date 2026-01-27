package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Service for GitHub GraphQL API operations.
 */
public class GitHubGraphQLService implements GraphQLService {

	private static final Logger logger = LoggerFactory.getLogger(GitHubGraphQLService.class);

	private final GitHubClient httpClient;

	private final ObjectMapper objectMapper;

	public GitHubGraphQLService(GitHubClient httpClient, ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public JsonNode executeQuery(String query, Object variables) {
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

		JsonNode result = executeQuery(query, variables);
		return result.path("data").path("repository").path("issues").path("totalCount").asInt(0);
	}

	// Get issue count using GitHub Search API for filtered queries
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

		JsonNode result = executeQuery(query, variables);
		return result.path("data").path("search").path("issueCount").asInt(0);
	}

	/**
	 * Execute GitHub search with sorting and limiting support for dashboard use cases
	 * @param searchQuery The formatted search query string
	 * @param sortBy Sort field (updated/created/comments/reactions) - converted to
	 * GraphQL format
	 * @param sortOrder Sort direction (desc/asc) - converted to GraphQL format
	 * @param first Number of issues to fetch (max limit enforced by GraphQL)
	 * @param after Cursor for pagination (null for first page)
	 * @return JsonNode containing search results with pagination info
	 */
	@Override
	public JsonNode searchIssuesWithSorting(String searchQuery, String sortBy, String sortOrder, int first,
			String after) {
		// Convert REST API sort parameters to GraphQL format
		String sortParam = convertSortToGraphQL(sortBy);
		String orderParam = convertOrderToGraphQL(sortOrder);

		String query = String.format("""
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
				                assignees(first: 10) {
				                    nodes {
				                        login
				                        ... on User {
				                            name
				                        }
				                    }
				                }
				                labels(first: 20) {
				                    nodes {
				                        name
				                        color
				                        description
				                    }
				                }
				                milestone {
				                    title
				                    number
				                    state
				                    description
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
				                        reactions {
				                            totalCount
				                        }
				                    }
				                }
				            }
				        }
				    }
				}
				""");

		Object variables = Map.of("query", buildSortedSearchQuery(searchQuery, sortParam, orderParam), "first", first,
				"after", after != null ? after : "");

		return executeQuery(query, variables);
	}

	/**
	 * Convert REST API sort parameter to GraphQL search query format GitHub Search API
	 * sorting is done via query qualifiers, not GraphQL orderBy
	 */
	private String buildSortedSearchQuery(String baseQuery, String sortBy, String sortOrder) {
		// GitHub Search API uses sort qualifiers in the query string
		// For GraphQL search, we need to add sort:field-direction to the query
		if (sortBy != null && !"updated".equals(sortBy)) {
			return baseQuery + " sort:" + sortBy + "-" + sortOrder;
		}
		// "updated" is the default sort, no need to add it explicitly
		return baseQuery;
	}

	/**
	 * Convert REST API sort field to GraphQL compatible format
	 */
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

	/**
	 * Convert REST API sort order to GraphQL compatible format
	 */
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
