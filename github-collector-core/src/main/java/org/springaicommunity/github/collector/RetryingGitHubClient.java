package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Decorator that adds automatic retry logic with smart backoff to a {@link GitHubClient}.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Exponential backoff for transient errors (5xx, network)</li>
 * <li>Reset-aware backoff for rate limit errors: sleeps until {@code X-RateLimit-Reset}
 * instead of blind exponential delay</li>
 * <li>Proactive pacing: injects delays when remaining rate limit is low to avoid hitting
 * the wall</li>
 * <li>Retries 403 rate limit errors (remaining=0) and 429 Too Many Requests</li>
 * </ul>
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
 *     .pacingThreshold(200)
 *     .build();
 * }
 * </pre>
 */
public final class RetryingGitHubClient implements GitHubClient {

	private static final Logger logger = LoggerFactory.getLogger(RetryingGitHubClient.class);

	/**
	 * Maximum time to wait for a rate limit reset (1 hour). If the computed wait exceeds
	 * this, fall back to exponential backoff.
	 */
	private static final long MAX_RESET_WAIT_SECONDS = 3600;

	private final GitHubClient delegate;

	private final int maxRetries;

	private final long initialDelayMs;

	private final int pacingThreshold;

	/**
	 * Private constructor - use {@link #builder()} to create instances.
	 */
	private RetryingGitHubClient(Builder builder) {
		this.delegate = builder.delegate;
		this.maxRetries = builder.maxRetries;
		this.initialDelayMs = builder.initialDelayMs;
		this.pacingThreshold = builder.pacingThreshold;
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

	@Override
	public RateLimitInfo getLastRateLimitInfo() {
		return delegate.getLastRateLimitInfo();
	}

	private String executeWithRetry(RequestSupplier supplier, String description) {
		Exception lastException = null;
		long delay = initialDelayMs;

		for (int attempt = 0; attempt <= maxRetries; attempt++) {
			try {
				String result = supplier.get();

				// Proactive pacing after successful responses
				paceIfNeeded(description);

				return result;
			}
			catch (GitHubHttpClient.GitHubApiException e) {
				lastException = e;

				// Don't retry client errors (4xx) except:
				// - 429 Too Many Requests
				// - 403 with remaining=0 (rate limit)
				if (e.getStatusCode() >= 400 && e.getStatusCode() < 500 && e.getStatusCode() != 429
						&& !e.isRateLimitError()) {
					throw e;
				}

				if (attempt < maxRetries) {
					long waitMs = computeWaitTime(e, delay);
					logger.warn("{} failed (attempt {}/{}): {}. Waiting {}ms...", description, attempt + 1,
							maxRetries + 1, e.getMessage(), waitMs);
					sleep(waitMs);
					delay *= 2; // Exponential backoff for next non-rate-limit error
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

	/**
	 * Compute how long to wait before retrying. For rate limit errors with a known reset
	 * time, waits until exactly that time (+1s buffer). Otherwise falls back to the
	 * default exponential backoff delay.
	 */
	private long computeWaitTime(GitHubHttpClient.GitHubApiException e, long defaultDelay) {
		if (e.isRateLimitError() && e.getResetEpochSeconds() > 0) {
			long nowSeconds = Instant.now().getEpochSecond();
			long waitSeconds = e.getResetEpochSeconds() - nowSeconds + 1; // +1s buffer

			if (waitSeconds > 0 && waitSeconds <= MAX_RESET_WAIT_SECONDS) {
				logger.info("Rate limit exceeded. Waiting {} seconds until reset at epoch {}", waitSeconds,
						e.getResetEpochSeconds());
				return waitSeconds * 1000;
			}
			else if (waitSeconds > MAX_RESET_WAIT_SECONDS) {
				logger.warn("Rate limit reset is {} seconds away (> 1hr), using exponential backoff instead",
						waitSeconds);
			}
			// waitSeconds <= 0 means reset is in the past, use default delay
		}
		return defaultDelay;
	}

	/**
	 * Proactive pacing: after a successful request, check remaining rate limit and slow
	 * down to avoid hitting the wall. Spreads remaining requests evenly across time until
	 * reset.
	 */
	private void paceIfNeeded(String description) {
		RateLimitInfo info = delegate.getLastRateLimitInfo();
		if (info == null || info.remaining() < 0) {
			return;
		}

		if (info.remaining() > 0 && info.remaining() < pacingThreshold) {
			long nowSeconds = Instant.now().getEpochSecond();
			long secondsUntilReset = info.reset() - nowSeconds;

			if (secondsUntilReset > 0 && info.remaining() > 0) {
				long paceMs = (secondsUntilReset * 1000) / info.remaining();
				paceMs = Math.min(paceMs, 10_000); // cap at 10s
				paceMs = Math.max(paceMs, 100); // minimum 100ms

				logger.debug("Pacing: {}/{} remaining, sleeping {}ms ({})", info.remaining(), info.limit(), paceMs,
						description);
				sleep(paceMs);
			}
		}
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
	 * <li>pacingThreshold: 100 (start pacing when remaining drops below this)</li>
	 * </ul>
	 */
	public static class Builder {

		private GitHubClient delegate;

		private int maxRetries = 3;

		private long initialDelayMs = 1000;

		private int pacingThreshold = 100;

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
		 * Set the remaining request threshold for proactive pacing. When the number of
		 * remaining requests drops below this value, the client will start inserting
		 * delays to spread requests evenly until the rate limit resets.
		 * @param threshold remaining request threshold (default: 100)
		 * @return this builder
		 */
		public Builder pacingThreshold(int threshold) {
			this.pacingThreshold = threshold;
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
