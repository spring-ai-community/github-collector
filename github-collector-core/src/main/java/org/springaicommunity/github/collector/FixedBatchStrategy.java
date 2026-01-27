package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple fixed-size batch strategy.
 *
 * <p>
 * Always uses the requested batch size without adaptation.
 */
public class FixedBatchStrategy implements BatchStrategy {

	@Override
	public List<JsonNode> createBatch(List<JsonNode> pendingItems, int maxBatchSize) {
		if (pendingItems.isEmpty()) {
			return new ArrayList<>();
		}

		int batchSize = Math.min(maxBatchSize, pendingItems.size());
		List<JsonNode> batch = new ArrayList<>(pendingItems.subList(0, batchSize));

		// Remove processed items from pending list
		pendingItems.subList(0, batchSize).clear();

		return batch;
	}

	@Override
	public int calculateBatchSize(List<JsonNode> sampleItems, int requestedBatchSize) {
		// Fixed strategy always returns the requested size
		return requestedBatchSize;
	}

}
