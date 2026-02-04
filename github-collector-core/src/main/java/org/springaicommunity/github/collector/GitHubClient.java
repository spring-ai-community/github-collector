package org.springaicommunity.github.collector;

/**
 * Interface for GitHub API HTTP operations.
 *
 * <p>
 * Provides abstraction over the GitHub REST and GraphQL APIs, enabling testability and
 * potential decorator implementations (caching, retrying, logging).
 */
public interface GitHubClient {

	/**
	 * Execute a GET request to the GitHub REST API.
	 * @param path API path (e.g., "/repos/owner/repo") or full URL
	 * @return Response body as String
	 * @throws GitHubHttpClient.GitHubApiException if the request fails
	 */
	String get(String path);

	/**
	 * Execute a GET request with query parameters.
	 * @param path API path (without query string)
	 * @param queryString Query string (without leading ?)
	 * @return Response body as String
	 * @throws GitHubHttpClient.GitHubApiException if the request fails
	 */
	String getWithQuery(String path, String queryString);

	/**
	 * Execute a POST request to the GitHub GraphQL API.
	 * @param body Request body (JSON)
	 * @return Response body as String
	 * @throws GitHubHttpClient.GitHubApiException if the request fails
	 */
	String postGraphQL(String body);

	/**
	 * Get the rate limit information from the most recent API response. Returns null if
	 * no rate limit headers have been observed yet.
	 * @return last observed RateLimitInfo, or null
	 */
	default RateLimitInfo getLastRateLimitInfo() {
		return null;
	}

}
