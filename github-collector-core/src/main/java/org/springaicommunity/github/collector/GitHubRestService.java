package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for GitHub REST API operations.
 *
 * <p>
 * Converts GitHub API JSON responses to strongly-typed DTOs at the service boundary.
 */
public class GitHubRestService implements RestService {

	private static final Logger logger = LoggerFactory.getLogger(GitHubRestService.class);

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

	private final GitHubClient httpClient;

	private final ObjectMapper objectMapper;

	public GitHubRestService(GitHubClient httpClient, ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public RateLimitInfo getRateLimit() throws IOException {
		try {
			String response = httpClient.get("/rate_limit");
			JsonNode root = objectMapper.readTree(response);
			JsonNode core = root.path("resources").path("core");
			return new RateLimitInfo(core.path("limit").asInt(), core.path("remaining").asInt(),
					core.path("reset").asLong(), core.path("used").asInt());
		}
		catch (Exception e) {
			throw new IOException("Failed to get rate limit: " + e.getMessage(), e);
		}
	}

	@Override
	public RepositoryInfo getRepository(String repoName) throws IOException {
		try {
			String response = httpClient.get("/repos/" + repoName);
			JsonNode node = objectMapper.readTree(response);
			return new RepositoryInfo(node.path("id").asLong(), node.path("name").asText(),
					node.path("full_name").asText(), node.path("description").asText(null),
					node.path("html_url").asText(), node.path("private").asBoolean(),
					node.path("default_branch").asText("main"));
		}
		catch (Exception e) {
			throw new IOException("Failed to get repository: " + e.getMessage(), e);
		}
	}

	@Override
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

	@Override
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

	@Override
	public String buildSearchQuery(String owner, String repo, String state, List<String> labels, String labelMode) {
		StringBuilder query = new StringBuilder();
		query.append("repo:").append(owner).append("/").append(repo);
		query.append(" is:issue");

		if (!"all".equals(state)) {
			query.append(" is:").append(state);
		}

		if (labels != null && !labels.isEmpty()) {
			if ("all".equals(labelMode)) {
				for (String label : labels) {
					query.append(" label:\"").append(label).append("\"");
				}
			}
			else {
				query.append(" label:\"").append(labels.get(0)).append("\"");
				if (labels.size() > 1) {
					logger.warn(
							"Label mode 'any' with multiple labels - using first label only due to GitHub API limitations");
				}
			}
		}

		return query.toString();
	}

	@Override
	public PullRequest getPullRequest(String owner, String repo, int prNumber) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
			JsonNode node = objectMapper.readTree(response);
			return parsePullRequest(node);
		}
		catch (Exception e) {
			logger.error("Failed to get PR {}: {}", prNumber, e.getMessage());
			throw new RuntimeException("Failed to get PR #" + prNumber, e);
		}
	}

	@Override
	public List<Review> getPullRequestReviews(String owner, String repo, int prNumber) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews");
			JsonNode nodes = objectMapper.readTree(response);
			return parseReviews(nodes);
		}
		catch (Exception e) {
			logger.error("Failed to get reviews for PR {}: {}", prNumber, e.getMessage());
			return List.of();
		}
	}

	@Override
	public List<IssueEvent> getIssueEvents(String owner, String repo, int issueNumber) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/events");
			JsonNode nodes = objectMapper.readTree(response);
			return parseIssueEvents(nodes);
		}
		catch (Exception e) {
			logger.error("Failed to get events for issue {}: {}", issueNumber, e.getMessage());
			return List.of();
		}
	}

	@Override
	public List<Collaborator> getRepositoryCollaborators(String owner, String repo) {
		try {
			String response = httpClient.get("/repos/" + owner + "/" + repo + "/collaborators?per_page=100");
			JsonNode nodes = objectMapper.readTree(response);
			return parseCollaborators(nodes);
		}
		catch (Exception e) {
			logger.error("Failed to get collaborators for {}/{}: {}", owner, repo, e.getMessage());
			return List.of();
		}
	}

	@Override
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

	@Override
	public String buildPRSearchQuery(String repository, String prState, List<String> labelFilters, String labelMode) {
		StringBuilder query = new StringBuilder();
		query.append("repo:").append(repository);
		query.append(" is:pr");

		if (!"all".equals(prState)) {
			if ("merged".equals(prState)) {
				query.append(" is:merged");
			}
			else {
				query.append(" is:").append(prState);
			}
		}

		if (labelFilters != null && !labelFilters.isEmpty()) {
			if ("any".equals(labelMode)) {
				if (labelFilters.size() > 1) {
					logger.warn(
							"Label mode 'any' with multiple labels - using first label only due to GitHub API limitations");
				}
				query.append(" label:\"").append(labelFilters.get(0)).append("\"");
			}
			else {
				for (String label : labelFilters) {
					query.append(" label:\"").append(label).append("\"");
				}
			}
		}

		return query.toString();
	}

	@Override
	public SearchResult<PullRequest> searchPRs(String searchQuery, int batchSize, @Nullable String cursor) {
		try {
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

			JsonNode searchResult = objectMapper.readTree(response);

			// Parse PRs from items
			List<PullRequest> prs = new ArrayList<>();
			JsonNode items = searchResult.path("items");
			if (items.isArray()) {
				for (JsonNode item : items) {
					PullRequest pr = parsePullRequestFromSearch(item);
					if (pr != null) {
						prs.add(pr);
					}
				}
			}

			// Determine pagination - if we got fewer than requested, no more pages
			boolean hasMore = prs.size() >= batchSize;
			String nextCursor = hasMore ? String.valueOf(page + 1) : null;

			return new SearchResult<>(prs, nextCursor, hasMore);
		}
		catch (Exception e) {
			logger.error("Failed to search PRs: {}", e.getMessage());
			return SearchResult.empty();
		}
	}

	// ========== JSON Parsing Methods ==========

	private @Nullable PullRequest parsePullRequest(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			return new PullRequest(node.path("number").asInt(), node.path("title").asText(""),
					node.path("body").asText(null), node.path("state").asText(""),
					parseDateTime(node.path("created_at").asText(null)),
					parseDateTime(node.path("updated_at").asText(null)),
					parseDateTime(node.path("closed_at").asText(null)),
					parseDateTime(node.path("merged_at").asText(null)), node.path("url").asText(""),
					node.path("html_url").asText(""), parseAuthor(node.path("user")), List.of(), // Comments
																									// not
																									// included
																									// in
																									// PR
																									// endpoint
					parseLabels(node.path("labels")), List.of(), // Reviews fetched
																	// separately
					node.path("draft").asBoolean(false), node.path("merged").asBoolean(false),
					node.path("merge_commit_sha").asText(null), node.path("head").path("ref").asText(null),
					node.path("base").path("ref").asText(null), node.path("additions").asInt(0),
					node.path("deletions").asInt(0), node.path("changed_files").asInt(0));
		}
		catch (Exception e) {
			logger.warn("Failed to parse PR: {}", e.getMessage());
			return null;
		}
	}

	private @Nullable PullRequest parsePullRequestFromSearch(JsonNode node) {
		// Search API returns different structure than PR endpoint
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			return new PullRequest(node.path("number").asInt(), node.path("title").asText(""),
					node.path("body").asText(null), node.path("state").asText(""),
					parseDateTime(node.path("created_at").asText(null)),
					parseDateTime(node.path("updated_at").asText(null)),
					parseDateTime(node.path("closed_at").asText(null)), null, // merged_at
																				// not in
																				// search
																				// results
					node.path("url").asText(""), node.path("html_url").asText(""), parseAuthor(node.path("user")),
					List.of(), parseLabels(node.path("labels")), List.of(), // Reviews
																			// fetched
																			// separately
					node.path("draft").asBoolean(false), false, // merged not reliably in
																// search
					null, // merge_commit_sha not in search
					null, null, // head/base refs not in search
					0, 0, 0 // additions/deletions/changed_files not in search
			);
		}
		catch (Exception e) {
			logger.warn("Failed to parse PR from search: {}", e.getMessage());
			return null;
		}
	}

	private List<Review> parseReviews(JsonNode nodes) {
		List<Review> reviews = new ArrayList<>();
		if (nodes != null && nodes.isArray()) {
			for (JsonNode node : nodes) {
				Review review = parseReview(node);
				if (review != null) {
					reviews.add(review);
				}
			}
		}
		return reviews;
	}

	private @Nullable Review parseReview(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			return new Review(node.path("id").asLong(), node.path("body").asText(""), node.path("state").asText(""),
					parseDateTime(node.path("submitted_at").asText(null)), parseAuthor(node.path("user")),
					node.path("author_association").asText(""), node.path("html_url").asText(""));
		}
		catch (Exception e) {
			logger.warn("Failed to parse review: {}", e.getMessage());
			return null;
		}
	}

	private Author parseAuthor(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return new Author("unknown", null);
		}
		return new Author(node.path("login").asText("unknown"), node.path("name").asText(null));
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

	private List<IssueEvent> parseIssueEvents(JsonNode nodes) {
		List<IssueEvent> events = new ArrayList<>();
		if (nodes != null && nodes.isArray()) {
			for (JsonNode node : nodes) {
				IssueEvent event = parseIssueEvent(node);
				if (event != null) {
					events.add(event);
				}
			}
		}
		return events;
	}

	private @Nullable IssueEvent parseIssueEvent(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			String eventType = node.path("event").asText("");
			Label label = null;

			// Only "labeled" and "unlabeled" events have a label field
			if ("labeled".equals(eventType) || "unlabeled".equals(eventType)) {
				JsonNode labelNode = node.path("label");
				if (!labelNode.isMissingNode() && !labelNode.isNull()) {
					label = new Label(labelNode.path("name").asText(""), labelNode.path("color").asText(null),
							labelNode.path("description").asText(null));
				}
			}

			return new IssueEvent(node.path("id").asLong(), eventType, parseAuthor(node.path("actor")), label,
					parseDateTime(node.path("created_at").asText(null)));
		}
		catch (Exception e) {
			logger.warn("Failed to parse issue event: {}", e.getMessage());
			return null;
		}
	}

	private List<Collaborator> parseCollaborators(JsonNode nodes) {
		List<Collaborator> collaborators = new ArrayList<>();
		if (nodes != null && nodes.isArray()) {
			for (JsonNode node : nodes) {
				Collaborator collaborator = parseCollaborator(node);
				if (collaborator != null) {
					collaborators.add(collaborator);
				}
			}
		}
		return collaborators;
	}

	private @Nullable Collaborator parseCollaborator(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}

		try {
			Collaborator.Permissions permissions = null;
			JsonNode permNode = node.path("permissions");
			if (!permNode.isMissingNode() && !permNode.isNull()) {
				permissions = new Collaborator.Permissions(permNode.path("admin").asBoolean(false),
						permNode.path("maintain").asBoolean(false), permNode.path("push").asBoolean(false),
						permNode.path("triage").asBoolean(false), permNode.path("pull").asBoolean(false));
			}

			return new Collaborator(node.path("login").asText(""), node.path("id").asLong(),
					node.path("type").asText("User"), permissions, node.path("role_name").asText(null));
		}
		catch (Exception e) {
			logger.warn("Failed to parse collaborator: {}", e.getMessage());
			return null;
		}
	}

}
