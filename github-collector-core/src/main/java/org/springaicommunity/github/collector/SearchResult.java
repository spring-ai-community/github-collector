package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Result of a paginated search containing items and pagination info.
 *
 * @param <T> the type of items in the result
 * @param items the items returned from the search
 * @param nextCursor cursor for fetching the next page (null if no more pages)
 * @param hasMore whether there are more items available
 */
public record SearchResult<T>(List<T> items, String nextCursor, boolean hasMore) {

	/**
	 * Create an empty result with no more pages.
	 * @param <T> the item type
	 * @return empty SearchResult
	 */
	public static <T> SearchResult<T> empty() {
		return new SearchResult<>(List.of(), null, false);
	}

}
