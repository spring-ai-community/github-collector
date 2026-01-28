package org.springaicommunity.github.collector;

import java.time.Instant;

/**
 * Rate limit information from the GitHub API.
 *
 * <p>
 * This record captures the rate limit status for API requests, replacing the dependency
 * on org.kohsuke.github.GHRateLimit.
 *
 * @param limit the maximum number of requests allowed per hour
 * @param remaining the number of requests remaining in the current window
 * @param reset the time when the rate limit resets (epoch seconds)
 * @param used the number of requests used in the current window
 */
public record RateLimitInfo(int limit, int remaining, long reset, int used) {

	/**
	 * Returns the reset time as an Instant.
	 * @return the reset time
	 */
	public Instant getResetTime() {
		return Instant.ofEpochSecond(reset);
	}

	/**
	 * Returns true if the rate limit has been exceeded.
	 * @return true if no requests remaining
	 */
	public boolean isExceeded() {
		return remaining <= 0;
	}

}
