package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface for GitHub GraphQL API operations.
 *
 * <p>
 * Extracted to enable mocking in tests and support decorator pattern.
 */
public interface GraphQLService {

	/**
	 * Execute a GraphQL query with variables.
	 * @param query GraphQL query string
	 * @param variables Query variables (can be null)
	 * @return Query result as JsonNode
	 */
	JsonNode executeQuery(String query, Object variables);

	/**
	 * Get total issue count for a repository.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (OPEN, CLOSED)
	 * @return Total issue count
	 */
	int getTotalIssueCount(String owner, String repo, String state);

	/**
	 * Get issue count using GitHub Search API for filtered queries.
	 * @param searchQuery The search query string
	 * @return Total count of matching issues
	 */
	int getSearchIssueCount(String searchQuery);

	/**
	 * Execute GitHub search with sorting and limiting support for dashboard use cases.
	 * @param searchQuery The formatted search query string
	 * @param sortBy Sort field (updated/created/comments/reactions)
	 * @param sortOrder Sort direction (desc/asc)
	 * @param first Number of issues to fetch
	 * @param after Cursor for pagination (null for first page)
	 * @return JsonNode containing search results with pagination info
	 */
	JsonNode searchIssuesWithSorting(String searchQuery, String sortBy, String sortOrder, int first, String after);

}
