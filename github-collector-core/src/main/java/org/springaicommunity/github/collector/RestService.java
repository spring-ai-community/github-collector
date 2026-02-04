package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Interface for GitHub REST API operations.
 *
 * <p>
 * Returns strongly-typed DTOs instead of raw JSON to provide type safety and encapsulate
 * the GitHub API response structure.
 */
public interface RestService {

	/**
	 * Get current rate limit status.
	 * @return Rate limit information
	 * @throws IOException if API call fails
	 */
	RateLimitInfo getRateLimit() throws IOException;

	/**
	 * Get repository by name.
	 * @param repoName Repository name in "owner/repo" format
	 * @return Repository information
	 * @throws IOException if API call fails
	 */
	RepositoryInfo getRepository(String repoName) throws IOException;

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
	 * Build search query string for GitHub API.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (open/closed/all)
	 * @param labels List of labels to filter by
	 * @param labelMode Label matching mode (any/all)
	 * @return Formatted search query string
	 */
	String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode);

	/**
	 * Get specific pull request by number.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return PullRequest data
	 */
	PullRequest getPullRequest(String owner, String repo, int prNumber);

	/**
	 * Get reviews for a specific pull request.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return List of reviews
	 */
	List<Review> getPullRequestReviews(String owner, String repo, int prNumber);

	/**
	 * Get events for a specific issue.
	 *
	 * <p>
	 * Returns timeline events for an issue including label changes (labeled/unlabeled),
	 * state changes (closed/reopened), assignments, and more. Essential for tracking who
	 * applied labels and when.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param issueNumber Issue number
	 * @return List of issue events
	 */
	List<IssueEvent> getIssueEvents(String owner, String repo, int issueNumber);

	/**
	 * Get collaborators for a repository.
	 *
	 * <p>
	 * Returns users who have been granted access to the repository. Used to identify
	 * maintainers (users with push access or higher) for label authority analysis.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @return List of collaborators with their permissions
	 */
	List<Collaborator> getRepositoryCollaborators(String owner, String repo);

	/**
	 * Get releases for a repository.
	 *
	 * <p>
	 * Returns published releases for the repository, ordered by creation date descending.
	 * Used for H4 (External Validity) analysis - validating that issues mentioned in
	 * release notes match their labels.
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @return List of releases with release notes
	 */
	List<Release> getRepositoryReleases(String owner, String repo);

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
	 * @param createdAfter Only PRs created on or after this date (YYYY-MM-DD), or null
	 * @param createdBefore Only PRs created before this date (YYYY-MM-DD), or null
	 * @return Formatted search query
	 */
	String buildPRSearchQuery(String repository, String prState, List<String> labelFilters, String labelMode,
			@Nullable String createdAfter, @Nullable String createdBefore);

	/**
	 * Search for pull requests using GitHub Search API.
	 * @param searchQuery The formatted search query string
	 * @param batchSize Number of PRs to return per batch
	 * @param cursor Pagination cursor (page number as string, or null for first page)
	 * @return SearchResult containing PullRequest records and pagination info
	 */
	SearchResult<PullRequest> searchPRs(String searchQuery, int batchSize, @Nullable String cursor);

}
