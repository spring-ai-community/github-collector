package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Strategy interface for creating batches from pending items.
 *
 * <p>
 * Allows different batching strategies (fixed size, adaptive based on content size, etc.)
 */
public interface BatchStrategy {

	/**
	 * Create a batch from pending items. Items included in the batch should be removed
	 * from the pendingItems list.
	 * @param pendingItems mutable list of pending items (will be modified)
	 * @param maxBatchSize maximum number of items to include in the batch
	 * @return list of items for the current batch
	 */
	List<JsonNode> createBatch(List<JsonNode> pendingItems, int maxBatchSize);

	/**
	 * Calculate the recommended batch size based on item characteristics.
	 * @param sampleItems sample of items to analyze
	 * @param requestedBatchSize the originally requested batch size
	 * @return recommended batch size (may be smaller than requested for large items)
	 */
	int calculateBatchSize(List<JsonNode> sampleItems, int requestedBatchSize);

}
