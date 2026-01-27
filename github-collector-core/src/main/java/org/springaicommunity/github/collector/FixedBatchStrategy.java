package org.springaicommunity.github.collector;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple fixed-size batch strategy.
 *
 * <p>
 * Always uses the requested batch size without adaptation.
 *
 * @param <T> the type of items being batched
 */
public class FixedBatchStrategy<T> implements BatchStrategy<T> {

	@Override
	public List<T> createBatch(List<T> pendingItems, int maxBatchSize) {
		if (pendingItems.isEmpty()) {
			return new ArrayList<>();
		}

		int batchSize = Math.min(maxBatchSize, pendingItems.size());
		List<T> batch = new ArrayList<>(pendingItems.subList(0, batchSize));

		// Remove processed items from pending list
		pendingItems.subList(0, batchSize).clear();

		return batch;
	}

	@Override
	public int calculateBatchSize(List<T> sampleItems, int requestedBatchSize) {
		// Fixed strategy always returns the requested size
		return requestedBatchSize;
	}

}
