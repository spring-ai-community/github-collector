package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Decorator that adds automatic retry logic with exponential backoff to a
 * {@link GitHubClient}.
 *
 * <p>
 * Retries failed requests up to a configurable number of times with exponential backoff
 * between attempts. Only retries on transient errors (network issues, rate limiting).
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * // With defaults (3 retries, 1 second initial delay)
 * GitHubClient client = RetryingGitHubClient.builder()
 *     .wrapping(new GitHubHttpClient(token))
 *     .build();
 *
 * // With custom settings
 * GitHubClient client = RetryingGitHubClient.builder()
 *     .wrapping(new GitHubHttpClient(token))
 *     .maxRetries(5)
 *     .initialDelay(Duration.ofSeconds(2))
 *     .build();
 * }
 * </pre>
 */
public final class RetryingGitHubClient implements GitHubClient {

	private static final Logger logger = LoggerFactory.getLogger(RetryingGitHubClient.class);

	private final GitHubClient delegate;

	private final int maxRetries;

	private final long initialDelayMs;

	/**
	 * Private constructor - use {@link #builder()} to create instances.
	 */
	private RetryingGitHubClient(Builder builder) {
		this.delegate = builder.delegate;
		this.maxRetries = builder.maxRetries;
		this.initialDelayMs = builder.initialDelayMs;
	}

	/**
	 * Create a new builder for RetryingGitHubClient.
	 * @return new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String get(String path) {
		return executeWithRetry(() -> delegate.get(path), "GET " + path);
	}

	@Override
	public String getWithQuery(String path, String queryString) {
		String desc = "GET " + path + (queryString != null ? "?" + queryString : "");
		return executeWithRetry(() -> delegate.getWithQuery(path, queryString), desc);
	}

	@Override
	public String postGraphQL(String body) {
		return executeWithRetry(() -> delegate.postGraphQL(body), "POST GraphQL");
	}

	private String executeWithRetry(RequestSupplier supplier, String description) {
		Exception lastException = null;
		long delay = initialDelayMs;

		for (int attempt = 0; attempt <= maxRetries; attempt++) {
			try {
				return supplier.get();
			}
			catch (GitHubHttpClient.GitHubApiException e) {
				lastException = e;

				// Don't retry client errors (4xx except 429)
				if (e.getStatusCode() >= 400 && e.getStatusCode() < 500 && e.getStatusCode() != 429) {
					throw e;
				}

				if (attempt < maxRetries) {
					logger.warn("{} failed (attempt {}/{}): {}. Retrying in {}ms...", description, attempt + 1,
							maxRetries + 1, e.getMessage(), delay);
					sleep(delay);
					delay *= 2; // Exponential backoff
				}
			}
			catch (Exception e) {
				lastException = e;

				if (attempt < maxRetries) {
					logger.warn("{} failed (attempt {}/{}): {}. Retrying in {}ms...", description, attempt + 1,
							maxRetries + 1, e.getMessage(), delay);
					sleep(delay);
					delay *= 2;
				}
			}
		}

		logger.error("{} failed after {} attempts", description, maxRetries + 1);
		if (lastException instanceof RuntimeException) {
			throw (RuntimeException) lastException;
		}
		throw new RuntimeException("Request failed after " + (maxRetries + 1) + " attempts", lastException);
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Retry interrupted", e);
		}
	}

	@FunctionalInterface
	private interface RequestSupplier {

		String get() throws Exception;

	}

	/**
	 * Builder for {@link RetryingGitHubClient}.
	 *
	 * <p>
	 * Provides sensible defaults:
	 * <ul>
	 * <li>maxRetries: 3</li>
	 * <li>initialDelay: 1 second</li>
	 * </ul>
	 */
	public static class Builder {

		private GitHubClient delegate;

		private int maxRetries = 3;

		private long initialDelayMs = 1000;

		private Builder() {
		}

		/**
		 * Set the client to wrap with retry logic.
		 * @param client the GitHubClient to wrap (required)
		 * @return this builder
		 */
		public Builder wrapping(GitHubClient client) {
			this.delegate = client;
			return this;
		}

		/**
		 * Set the maximum number of retry attempts.
		 * @param maxRetries maximum retries (default: 3)
		 * @return this builder
		 */
		public Builder maxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		/**
		 * Set the initial delay between retries using Duration.
		 * @param delay initial delay (doubles on each retry, default: 1 second)
		 * @return this builder
		 */
		public Builder initialDelay(Duration delay) {
			this.initialDelayMs = delay.toMillis();
			return this;
		}

		/**
		 * Set the initial delay between retries in milliseconds.
		 * @param delayMs initial delay in milliseconds (doubles on each retry, default:
		 * 1000)
		 * @return this builder
		 */
		public Builder initialDelayMs(long delayMs) {
			this.initialDelayMs = delayMs;
			return this;
		}

		/**
		 * Build the RetryingGitHubClient.
		 * @return configured RetryingGitHubClient
		 * @throws IllegalStateException if required parameters are missing or invalid
		 */
		public RetryingGitHubClient build() {
			if (delegate == null) {
				throw new IllegalStateException("A GitHubClient to wrap is required. Call wrapping() first.");
			}
			if (maxRetries < 0) {
				throw new IllegalStateException("maxRetries must be non-negative");
			}
			if (initialDelayMs <= 0) {
				throw new IllegalStateException("initialDelay must be positive");
			}
			return new RetryingGitHubClient(this);
		}

	}

}
