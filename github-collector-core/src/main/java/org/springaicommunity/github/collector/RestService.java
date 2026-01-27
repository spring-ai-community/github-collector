package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.List;

/**
 * Interface for GitHub REST API operations.
 *
 * <p>
 * Extracted to enable mocking in tests and support decorator pattern.
 */
public interface RestService {

	/**
	 * Get current rate limit status.
	 * @return Rate limit information
	 * @throws IOException if API call fails
	 */
	GHRateLimit getRateLimit() throws IOException;

	/**
	 * Get repository by name.
	 * @param repoName Repository name in "owner/repo" format
	 * @return Repository object
	 * @throws IOException if API call fails
	 */
	GHRepository getRepository(String repoName) throws IOException;

	/**
	 * Get repository information as JSON.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @return Repository info as JsonNode
	 */
	JsonNode getRepositoryInfo(String owner, String repo);

	/**
	 * Get total issue count for a repository.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (open/closed/all)
	 * @return Total issue count
	 */
	int getTotalIssueCount(String owner, String repo, String state);

	/**
	 * Get total issue count with search parameters.
	 * @param searchQuery The formatted search query string
	 * @return Total number of issues matching the query
	 */
	int getTotalIssueCount(String searchQuery);

	/**
	 * Build search query string for GitHub API (backward compatible).
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (open/closed/all)
	 * @param labels List of labels to filter by
	 * @param labelMode Label matching mode (any/all)
	 * @return Formatted search query string
	 */
	String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode);

	/**
	 * Build search query string for GitHub API with dashboard enhancements.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (open/closed/all)
	 * @param labels List of labels to filter by
	 * @param labelMode Label matching mode (any/all)
	 * @param sortBy Sort field (updated/created/comments/reactions)
	 * @param sortOrder Sort direction (desc/asc)
	 * @param maxIssues Maximum number of issues to collect (null for unlimited)
	 * @return Formatted search query string
	 */
	String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode,
			String sortBy, String sortOrder, Integer maxIssues);

	/**
	 * Execute GitHub search with sorting and pagination support.
	 * @param searchQuery The formatted search query string
	 * @param sortBy Sort field (updated/created/comments/reactions)
	 * @param sortOrder Sort direction (desc/asc)
	 * @param perPage Number of issues per page (max 100)
	 * @param page Page number (1-based)
	 * @return JsonNode containing search results
	 */
	JsonNode searchIssues(String searchQuery, String sortBy, String sortOrder, int perPage, int page);

	/**
	 * Get specific pull request by number.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return PR data as JsonNode
	 */
	JsonNode getPullRequest(String owner, String repo, int prNumber);

	/**
	 * Get reviews for a specific pull request.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return Reviews data as JsonNode
	 */
	JsonNode getPullRequestReviews(String owner, String repo, int prNumber);

	/**
	 * Get total PR count with search parameters.
	 * @param searchQuery The formatted search query string
	 * @return Total number of PRs matching the query
	 */
	int getTotalPRCount(String searchQuery);

	/**
	 * Build GitHub search query for pull requests.
	 * @param repository Repository in format "owner/repo"
	 * @param prState PR state (open, closed, merged, all)
	 * @param labelFilters Optional label filters
	 * @param labelMode Label matching mode (any, all)
	 * @return Formatted search query
	 */
	String buildPRSearchQuery(String repository, String prState, List<String> labelFilters, String labelMode);

	/**
	 * Search for pull requests using GitHub Search API.
	 * @param searchQuery The formatted search query string
	 * @param batchSize Number of PRs to return per batch
	 * @param cursor Pagination cursor
	 * @return JSON response containing PR search results
	 */
	JsonNode searchPRs(String searchQuery, int batchSize, String cursor);

}
