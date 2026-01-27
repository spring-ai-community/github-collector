package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP client wrapper for GitHub API calls using Java 11+ HttpClient. Replaces
 * Spring's RestClient to keep the library Spring-free.
 */
public class GitHubHttpClient implements GitHubClient {

	private static final Logger logger = LoggerFactory.getLogger(GitHubHttpClient.class);

	private static final String GITHUB_API_BASE = "https://api.github.com";

	private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

	private final HttpClient httpClient;

	private final String token;

	public GitHubHttpClient(String token) {
		this.token = token;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(30))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	@Override
	public String get(String path) {
		String url = path.startsWith("http") ? path : GITHUB_API_BASE + path;
		logger.debug("GET {}", url);
		long start = System.currentTimeMillis();

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Authorization", "token " + token)
			.header("Accept", "application/vnd.github.v3+json")
			.header("User-Agent", "github-collector")
			.GET()
			.build();

		try {
			String response = executeRequest(request);
			logger.debug("GET {} completed in {}ms ({} bytes)", url, System.currentTimeMillis() - start,
					response.length());
			return response;
		}
		catch (Exception e) {
			logger.debug("GET {} failed after {}ms: {}", url, System.currentTimeMillis() - start, e.getMessage());
			throw e;
		}
	}

	@Override
	public String getWithQuery(String path, String queryString) {
		String url = GITHUB_API_BASE + path;
		if (queryString != null && !queryString.isEmpty()) {
			url += "?" + queryString;
		}
		return get(url);
	}

	@Override
	public String postGraphQL(String body) {
		logger.debug("POST GraphQL ({} bytes)", body.length());
		long start = System.currentTimeMillis();

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GITHUB_GRAPHQL_ENDPOINT))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "application/json")
			.header("User-Agent", "github-collector")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		try {
			String response = executeRequest(request);
			logger.debug("POST GraphQL completed in {}ms ({} bytes)", System.currentTimeMillis() - start,
					response.length());
			return response;
		}
		catch (Exception e) {
			logger.debug("POST GraphQL failed after {}ms: {}", System.currentTimeMillis() - start, e.getMessage());
			throw e;
		}
	}

	private String executeRequest(HttpRequest request) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			int statusCode = response.statusCode();
			if (statusCode >= 200 && statusCode < 300) {
				return response.body();
			}
			else if (statusCode == 401) {
				throw new GitHubApiException("Unauthorized: Bad credentials. Check your GITHUB_TOKEN.", statusCode,
						response.body());
			}
			else if (statusCode == 403) {
				// Check for rate limiting
				String rateLimitRemaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("");
				if ("0".equals(rateLimitRemaining)) {
					String resetTime = response.headers().firstValue("X-RateLimit-Reset").orElse("unknown");
					throw new GitHubApiException("Rate limit exceeded. Resets at: " + resetTime, statusCode,
							response.body());
				}
				throw new GitHubApiException("Forbidden: " + response.body(), statusCode, response.body());
			}
			else if (statusCode == 404) {
				throw new GitHubApiException("Not found: " + request.uri(), statusCode, response.body());
			}
			else {
				throw new GitHubApiException("GitHub API error: " + statusCode, statusCode, response.body());
			}
		}
		catch (IOException e) {
			logger.error("HTTP request failed: {}", e.getMessage());
			throw new GitHubApiException("HTTP request failed: " + e.getMessage(), e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GitHubApiException("HTTP request interrupted", e);
		}
	}

	/**
	 * Exception thrown when GitHub API calls fail.
	 */
	public static class GitHubApiException extends RuntimeException {

		private final int statusCode;

		private final String responseBody;

		public GitHubApiException(String message, int statusCode, String responseBody) {
			super(message);
			this.statusCode = statusCode;
			this.responseBody = responseBody;
		}

		public GitHubApiException(String message, Throwable cause) {
			super(message, cause);
			this.statusCode = -1;
			this.responseBody = null;
		}

		public int getStatusCode() {
			return statusCode;
		}

		public String getResponseBody() {
			return responseBody;
		}

	}

}
