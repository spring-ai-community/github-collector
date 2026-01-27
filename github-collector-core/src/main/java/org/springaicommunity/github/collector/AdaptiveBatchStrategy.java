package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive batch strategy that adjusts batch size based on item content size.
 *
 * <p>
 * Reduces batch size when items are large to prevent memory issues and improve processing
 * efficiency.
 */
public class AdaptiveBatchStrategy implements BatchStrategy {

	private static final Logger logger = LoggerFactory.getLogger(AdaptiveBatchStrategy.class);

	private final ObjectMapper objectMapper;

	private final int largeItemThreshold;

	/**
	 * Create an adaptive batch strategy.
	 * @param objectMapper for serializing items to estimate size
	 * @param largeItemThreshold average item size (in bytes) above which batch size is
	 * reduced
	 */
	public AdaptiveBatchStrategy(ObjectMapper objectMapper, int largeItemThreshold) {
		this.objectMapper = objectMapper;
		this.largeItemThreshold = largeItemThreshold;
	}

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
		if (sampleItems.isEmpty()) {
			return requestedBatchSize;
		}

		try {
			// Calculate average size of sample items
			int totalSize = 0;
			for (JsonNode item : sampleItems) {
				String itemJson = objectMapper.writeValueAsString(item);
				totalSize += itemJson.length();
			}

			int averageSize = totalSize / sampleItems.size();

			// If items are large, reduce batch size
			if (averageSize > largeItemThreshold) {
				int adaptiveBatchSize = Math.max(1, requestedBatchSize / 2);
				logger.info("Large items detected (avg: {} bytes), reducing batch size to {}", averageSize,
						adaptiveBatchSize);
				return adaptiveBatchSize;
			}

			return requestedBatchSize;
		}
		catch (Exception e) {
			logger.warn("Failed to calculate adaptive batch size: {}", e.getMessage());
			return requestedBatchSize;
		}
	}

}
