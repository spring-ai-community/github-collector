package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Plans adaptive time windows for GitHub Search API collection.
 *
 * <p>
 * The GitHub Search API returns a maximum of 1,000 results per query regardless of
 * pagination. For repositories with more items than this limit, queries must be split
 * into smaller date ranges (time windows) where each window fits under the limit.
 *
 * <p>
 * This planner recursively binary-splits a date range until each window contains fewer
 * items than the configured threshold (default 900, providing a safety margin below the
 * 1,000 hard cap).
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * var planner = new AdaptiveWindowPlanner();
 * List<TimeWindow> windows = planner.planWindows(
 *     "2023-01-01", "2026-01-01",
 *     (after, before) -> searchService.getCount(repo, after, before));
 * }</pre>
 */
public class AdaptiveWindowPlanner {

	private static final Logger logger = LoggerFactory.getLogger(AdaptiveWindowPlanner.class);

	/**
	 * Default maximum items per window (safety margin below GitHub's 1,000 hard cap).
	 */
	public static final int DEFAULT_MAX_PER_WINDOW = 900;

	private final int maxPerWindow;

	public AdaptiveWindowPlanner() {
		this(DEFAULT_MAX_PER_WINDOW);
	}

	public AdaptiveWindowPlanner(int maxPerWindow) {
		if (maxPerWindow <= 0) {
			throw new IllegalArgumentException("maxPerWindow must be positive, got: " + maxPerWindow);
		}
		this.maxPerWindow = maxPerWindow;
	}

	/**
	 * A date range representing a collection time window.
	 *
	 * @param createdAfter ISO date (inclusive), e.g. "2023-01-01"
	 * @param createdBefore ISO date (exclusive), e.g. "2024-01-01"
	 */
	public record TimeWindow(String createdAfter, String createdBefore) {
	}

	/**
	 * Plan time windows by recursively splitting the date range until each window fits
	 * under the search API limit.
	 * @param createdAfter start date (ISO format, inclusive)
	 * @param createdBefore end date (ISO format, exclusive)
	 * @param countFunction function that takes (createdAfter, createdBefore) and returns
	 * the number of items in that range. Should return -1 on error.
	 * @return ordered list of non-overlapping time windows covering the full range
	 */
	public List<TimeWindow> planWindows(String createdAfter, String createdBefore,
			BiFunction<String, String, Integer> countFunction) {
		List<TimeWindow> windows = new ArrayList<>();
		planWindowsRecursive(createdAfter, createdBefore, countFunction, windows, 0);
		return windows;
	}

	private void planWindowsRecursive(String createdAfter, String createdBefore,
			BiFunction<String, String, Integer> countFunction, List<TimeWindow> result, int depth) {

		String indent = "  ".repeat(depth + 1);

		int count = countFunction.apply(createdAfter, createdBefore);

		if (count < 0) {
			// Count failed — include the window as-is (best effort)
			logger.warn("{}Count query failed for {}/{}; including window as-is", indent, createdAfter, createdBefore);
			result.add(new TimeWindow(createdAfter, createdBefore));
			return;
		}

		logger.info("{}{} to {}: {} items", indent, createdAfter, createdBefore, count);

		if (count <= maxPerWindow) {
			result.add(new TimeWindow(createdAfter, createdBefore));
			return;
		}

		// Check if we can still split
		LocalDate start = LocalDate.parse(createdAfter);
		LocalDate end = LocalDate.parse(createdBefore);
		long days = ChronoUnit.DAYS.between(start, end);

		if (days <= 1) {
			// Can't split further — include as-is with a warning
			logger.warn("{}Cannot split further ({} to {}, {} items > {}); including oversized window", indent,
					createdAfter, createdBefore, count, maxPerWindow);
			result.add(new TimeWindow(createdAfter, createdBefore));
			return;
		}

		// Binary split
		LocalDate mid = start.plusDays(days / 2);
		String midStr = mid.toString();

		logger.info("{}Splitting at {} ({}d left, {}d right)", indent, midStr, ChronoUnit.DAYS.between(start, mid),
				ChronoUnit.DAYS.between(mid, end));

		planWindowsRecursive(createdAfter, midStr, countFunction, result, depth + 1);
		planWindowsRecursive(midStr, createdBefore, countFunction, result, depth + 1);
	}

}
