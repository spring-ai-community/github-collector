package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for GitHub REST API operations.
 */
public class GitHubRestService {

	private static final Logger logger = LoggerFactory.getLogger(GitHubRestService.class);

	private final GitHub gitHub;

	private final GitHubHttpClient httpClient;

	private final ObjectMapper objectMapper;

	public GitHubRestService(GitHub gitHub, GitHubHttpClient httpClient, ObjectMapper objectMapper) {
		this.gitHub = gitHub;
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
	}

	public GHRateLimit getRateLimit() throws IOException {
		return gitHub.getRateLimit();
	}

	public GHRepository getRepository(String repoName) throws IOException {
		return gitHub.getRepository(repoName);
	}

	public JsonNode getRepositoryInfo(String owner, String repo) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo);
			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("Failed to parse repository info: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

	public int getTotalIssueCount(String owner, String repo, String state) {
		try {
			String query = String.format("repo:%s/%s is:issue is:%s", owner, repo, state);
			String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
			String response = httpClient.get("/search/issues?q=" + encodedQuery);

			JsonNode searchResult = objectMapper.readTree(response);
			return searchResult.path("total_count").asInt(0);
		}
		catch (Exception e) {
			logger.error("Failed to get total issue count: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * Build search query string for GitHub API (backward compatible)
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param state Issue state (open/closed/all)
	 * @param labels List of labels to filter by
	 * @param labelMode Label matching mode (any/all)
	 * @return Formatted search query string
	 */
	public String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode) {
		return buildSearchQuery(owner, repo, state, labels, labelMode, null, null, null);
	}

	/**
	 * Build search query string for GitHub API with dashboard enhancements
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
	public String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode,
			String sortBy, String sortOrder, Integer maxIssues) {
		StringBuilder query = new StringBuilder();
		query.append("repo:").append(owner).append("/").append(repo);
		query.append(" is:issue");

		if (!"all".equals(state)) {
			query.append(" is:").append(state);
		}

		if (labels != null && !labels.isEmpty()) {
			if ("all".equals(labelMode)) {
				// For "all" mode, add each label requirement
				for (String label : labels) {
					query.append(" label:\"").append(label).append("\"");
				}
			}
			else {
				// For "any" mode, use first label only due to GitHub API limitations
				query.append(" label:\"").append(labels.get(0)).append("\"");
				if (labels.size() > 1) {
					logger.warn(
							"Label mode 'any' with multiple labels - using first label only due to GitHub API limitations");
				}
			}
		}

		return query.toString();
	}

	/**
	 * Execute GitHub search with sorting and pagination support
	 * @param searchQuery The formatted search query string
	 * @param sortBy Sort field (updated/created/comments/reactions)
	 * @param sortOrder Sort direction (desc/asc)
	 * @param perPage Number of issues per page (max 100)
	 * @param page Page number (1-based)
	 * @return JsonNode containing search results
	 */
	public JsonNode searchIssues(String searchQuery, String sortBy, String sortOrder, int perPage, int page) {
		try {
			String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
			String url = String.format("/search/issues?q=%s&sort=%s&order=%s&per_page=%d&page=%d", encodedQuery, sortBy,
					sortOrder, perPage, page);
			String response = httpClient.get(url);

			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("Failed to search issues: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

	/**
	 * Get total issue count with search parameters
	 * @param searchQuery The formatted search query string
	 * @return Total number of issues matching the query
	 */
	public int getTotalIssueCount(String searchQuery) {
		try {
			String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
			String response = httpClient.get("/search/issues?q=" + encodedQuery);

			JsonNode searchResult = objectMapper.readTree(response);
			return searchResult.path("total_count").asInt(0);
		}
		catch (Exception e) {
			logger.error("Failed to get total issue count: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * Get specific pull request by number
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return PR data as JsonNode
	 */
	public JsonNode getPullRequest(String owner, String repo, int prNumber) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("Failed to get PR {}: {}", prNumber, e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

	/**
	 * Get reviews for a specific pull request
	 * @param owner Repository owner
	 * @param repo Repository name
	 * @param prNumber PR number
	 * @return Reviews data as JsonNode
	 */
	public JsonNode getPullRequestReviews(String owner, String repo, int prNumber) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews");
			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("Failed to get reviews for PR {}: {}", prNumber, e.getMessage());
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Get total PR count with search parameters
	 * @param searchQuery The formatted search query string
	 * @return Total number of PRs matching the query
	 */
	public int getTotalPRCount(String searchQuery) {
		try {
			String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
			String response = httpClient.get("/search/issues?q=" + encodedQuery);

			JsonNode searchResult = objectMapper.readTree(response);
			return searchResult.path("total_count").asInt(0);
		}
		catch (Exception e) {
			logger.error("Failed to get total PR count: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * Build GitHub search query for pull requests
	 * @param repository Repository in format "owner/repo"
	 * @param prState PR state (open, closed, merged, all)
	 * @param labelFilters Optional label filters
	 * @param labelMode Label matching mode (any, all)
	 * @return Formatted search query
	 */
	public String buildPRSearchQuery(String repository, String prState, List<String> labelFilters, String labelMode) {
		StringBuilder query = new StringBuilder();
		query.append("repo:").append(repository);
		query.append(" is:pr");

		// Add state filter
		if (!"all".equals(prState)) {
			if ("merged".equals(prState)) {
				query.append(" is:merged");
			}
			else {
				query.append(" is:").append(prState);
			}
		}

		// Add label filters
		if (labelFilters != null && !labelFilters.isEmpty()) {
			if ("any".equals(labelMode)) {
				// GitHub API limitation: can only search for one label at a time with OR
				// logic
				// Use first label only and warn user
				if (labelFilters.size() > 1) {
					logger.warn(
							"Label mode 'any' with multiple labels - using first label only due to GitHub API limitations");
				}
				query.append(" label:\"").append(labelFilters.get(0)).append("\"");
			}
			else {
				// "all" mode - add each label (AND logic)
				for (String label : labelFilters) {
					query.append(" label:\"").append(label).append("\"");
				}
			}
		}

		return query.toString();
	}

	/**
	 * Search for pull requests using GitHub Search API
	 * @param searchQuery The formatted search query string
	 * @param batchSize Number of PRs to return per batch
	 * @param cursor Pagination cursor (for this REST API implementation, we'll use page
	 * numbers)
	 * @return JSON response containing PR search results
	 */
	public JsonNode searchPRs(String searchQuery, int batchSize, String cursor) {
		try {
			// For REST API, we'll use simple pagination instead of cursor
			int page = 1;
			if (cursor != null && !cursor.isEmpty()) {
				try {
					page = Integer.parseInt(cursor);
				}
				catch (NumberFormatException e) {
					logger.warn("Invalid cursor format, using page 1: {}", cursor);
				}
			}

			String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
			String url = String.format("/search/issues?q=%s&per_page=%d&page=%d", encodedQuery, batchSize, page);
			String response = httpClient.get(url);

			return objectMapper.readTree(response);
		}
		catch (Exception e) {
			logger.error("Failed to search PRs: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

}
