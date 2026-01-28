package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FixedBatchStrategy}.
 *
 * Tests fixed batch behavior - always uses requested batch size without adaptation.
 */
@DisplayName("FixedBatchStrategy Tests")
class FixedBatchStrategyTest {

	private FixedBatchStrategy strategy;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		strategy = new FixedBatchStrategy();
		objectMapper = ObjectMapperFactory.create();
	}

	@Nested
	@DisplayName("Batch Creation Tests")
	class BatchCreationTest {

		@Test
		@DisplayName("Should create batch with requested size")
		void shouldCreateBatchWithRequestedSize() {
			List<JsonNode> pendingItems = createPendingItems(10);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 5);

			assertThat(batch).hasSize(5);
			assertThat(pendingItems).hasSize(5);
		}

		@Test
		@DisplayName("Should return all items when fewer than batch size")
		void shouldReturnAllItemsWhenFewerThanBatchSize() {
			List<JsonNode> pendingItems = createPendingItems(3);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 10);

			assertThat(batch).hasSize(3);
			assertThat(pendingItems).isEmpty();
		}

		@Test
		@DisplayName("Should return empty list for empty pending items")
		void shouldReturnEmptyListForEmptyPendingItems() {
			List<JsonNode> pendingItems = new ArrayList<>();

			List<JsonNode> batch = strategy.createBatch(pendingItems, 10);

			assertThat(batch).isEmpty();
		}

		@Test
		@DisplayName("Should remove processed items from pending list")
		void shouldRemoveProcessedItemsFromPendingList() {
			List<JsonNode> pendingItems = createPendingItems(10);
			JsonNode sixthItem = pendingItems.get(5);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 5);

			assertThat(batch).hasSize(5);
			assertThat(pendingItems).hasSize(5);
			assertThat(pendingItems.get(0)).isEqualTo(sixthItem);
		}

		@Test
		@DisplayName("Should handle batch size of 1")
		void shouldHandleBatchSizeOfOne() {
			List<JsonNode> pendingItems = createPendingItems(5);
			JsonNode firstItem = pendingItems.get(0);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 1);

			assertThat(batch).hasSize(1);
			assertThat(batch.get(0)).isEqualTo(firstItem);
			assertThat(pendingItems).hasSize(4);
		}

		@Test
		@DisplayName("Should handle multiple consecutive batch creations")
		void shouldHandleMultipleConsecutiveBatchCreations() {
			List<JsonNode> pendingItems = createPendingItems(10);

			List<JsonNode> batch1 = strategy.createBatch(pendingItems, 3);
			List<JsonNode> batch2 = strategy.createBatch(pendingItems, 3);
			List<JsonNode> batch3 = strategy.createBatch(pendingItems, 3);
			List<JsonNode> batch4 = strategy.createBatch(pendingItems, 3);

			assertThat(batch1).hasSize(3);
			assertThat(batch2).hasSize(3);
			assertThat(batch3).hasSize(3);
			assertThat(batch4).hasSize(1); // Only 1 item remaining
			assertThat(pendingItems).isEmpty();
		}

	}

	@Nested
	@DisplayName("Batch Size Calculation Tests")
	class BatchSizeCalculationTest {

		@Test
		@DisplayName("Should always return requested batch size")
		void shouldAlwaysReturnRequestedBatchSize() {
			List<JsonNode> sampleItems = createPendingItems(5);

			int batchSize = strategy.calculateBatchSize(sampleItems, 100);

			assertThat(batchSize).isEqualTo(100);
		}

		@Test
		@DisplayName("Should return requested size for empty sample")
		void shouldReturnRequestedSizeForEmptySample() {
			List<JsonNode> sampleItems = new ArrayList<>();

			int batchSize = strategy.calculateBatchSize(sampleItems, 50);

			assertThat(batchSize).isEqualTo(50);
		}

		@Test
		@DisplayName("Should return requested size regardless of item content")
		void shouldReturnRequestedSizeRegardlessOfItemContent() {
			// Create very large items
			List<JsonNode> largeItems = createLargeItems(5, 100000);

			int batchSize = strategy.calculateBatchSize(largeItems, 100);

			// Fixed strategy ignores item size
			assertThat(batchSize).isEqualTo(100);
		}

		@Test
		@DisplayName("Should return requested size for any positive value")
		void shouldReturnRequestedSizeForAnyPositiveValue() {
			List<JsonNode> sampleItems = createPendingItems(3);

			assertThat(strategy.calculateBatchSize(sampleItems, 1)).isEqualTo(1);
			assertThat(strategy.calculateBatchSize(sampleItems, 10)).isEqualTo(10);
			assertThat(strategy.calculateBatchSize(sampleItems, 1000)).isEqualTo(1000);
		}

		@Test
		@DisplayName("Should ignore sample items completely")
		void shouldIgnoreSampleItemsCompletely() {
			// Even with null-containing items, should just return requested size
			List<JsonNode> sampleItems = createPendingItems(10);

			int batchSize = strategy.calculateBatchSize(sampleItems, 25);

			assertThat(batchSize).isEqualTo(25);
		}

	}

	// Helper methods

	private List<JsonNode> createPendingItems(int count) {
		List<JsonNode> items = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("id", i);
			node.put("name", "item-" + i);
			items.add(node);
		}
		return items;
	}

	private List<JsonNode> createLargeItems(int count, int approximateSize) {
		List<JsonNode> items = new ArrayList<>();
		String padding = "x".repeat(approximateSize);

		for (int i = 0; i < count; i++) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("id", i);
			node.put("data", padding);
			items.add(node);
		}
		return items;
	}

}
