package org.springaicommunity.github.collector;

/**
 * Interface for GitHub GraphQL API operations.
 *
 * <p>
 * Returns strongly-typed DTOs instead of raw JSON to provide type safety and encapsulate
 * the GitHub API response structure.
 */
public interface GraphQLService {

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
	 * Search for issues with sorting and pagination support.
	 * @param searchQuery The formatted search query string
	 * @param sortBy Sort field (updated/created/comments/reactions)
	 * @param sortOrder Sort direction (desc/asc)
	 * @param first Number of issues to fetch
	 * @param after Cursor for pagination (null for first page)
	 * @return SearchResult containing Issue records and pagination info
	 */
	SearchResult<Issue> searchIssues(String searchQuery, String sortBy, String sortOrder, int first, String after);

}
