package org.springaicommunity.github.collector;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AdaptiveBatchStrategy}.
 *
 * Tests batch creation, size calculation, and threshold behavior.
 */
@DisplayName("AdaptiveBatchStrategy Tests")
class AdaptiveBatchStrategyTest {

	private ObjectMapper objectMapper;

	private AdaptiveBatchStrategy strategy;

	@BeforeEach
	void setUp() {
		objectMapper = ObjectMapperFactory.create();
		// Default threshold of 10000 bytes
		strategy = new AdaptiveBatchStrategy(objectMapper, 10000);
	}

	@Nested
	@DisplayName("Batch Creation Tests")
	class BatchCreationTest {

		@Test
		@DisplayName("Should create batch with requested size when enough items available")
		void shouldCreateBatchWithRequestedSize() {
			List<JsonNode> pendingItems = createPendingItems(10);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 5);

			assertThat(batch).hasSize(5);
			assertThat(pendingItems).hasSize(5);
		}

		@Test
		@DisplayName("Should create smaller batch when fewer items available")
		void shouldCreateSmallerBatchWhenFewerItemsAvailable() {
			List<JsonNode> pendingItems = createPendingItems(3);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 10);

			assertThat(batch).hasSize(3);
			assertThat(pendingItems).isEmpty();
		}

		@Test
		@DisplayName("Should remove items from pending list after batch creation")
		void shouldRemoveItemsFromPendingList() {
			List<JsonNode> pendingItems = createPendingItems(5);
			JsonNode firstItem = pendingItems.get(0);
			JsonNode lastItem = pendingItems.get(4);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 3);

			// Batch should contain first 3 items
			assertThat(batch).hasSize(3);
			assertThat(batch.get(0)).isEqualTo(firstItem);

			// Pending should contain remaining 2 items
			assertThat(pendingItems).hasSize(2);
			assertThat(pendingItems.get(1)).isEqualTo(lastItem);
		}

		@Test
		@DisplayName("Should return empty list when no pending items")
		void shouldReturnEmptyListWhenNoPendingItems() {
			List<JsonNode> pendingItems = new ArrayList<>();

			List<JsonNode> batch = strategy.createBatch(pendingItems, 10);

			assertThat(batch).isEmpty();
			assertThat(pendingItems).isEmpty();
		}

		@Test
		@DisplayName("Should handle batch size of 1")
		void shouldHandleBatchSizeOfOne() {
			List<JsonNode> pendingItems = createPendingItems(5);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 1);

			assertThat(batch).hasSize(1);
			assertThat(pendingItems).hasSize(4);
		}

		@Test
		@DisplayName("Should handle batch size larger than pending items")
		void shouldHandleBatchSizeLargerThanPendingItems() {
			List<JsonNode> pendingItems = createPendingItems(3);

			List<JsonNode> batch = strategy.createBatch(pendingItems, 100);

			assertThat(batch).hasSize(3);
			assertThat(pendingItems).isEmpty();
		}

	}

	@Nested
	@DisplayName("Batch Size Calculation Tests")
	class BatchSizeCalculationTest {

		@Test
		@DisplayName("Should return requested size for small items")
		void shouldReturnRequestedSizeForSmallItems() {
			List<JsonNode> sampleItems = createSmallItems(5);

			int batchSize = strategy.calculateBatchSize(sampleItems, 100);

			assertThat(batchSize).isEqualTo(100);
		}

		@Test
		@DisplayName("Should reduce batch size for large items")
		void shouldReduceBatchSizeForLargeItems() {
			// Create items larger than threshold (10000 bytes)
			List<JsonNode> sampleItems = createLargeItems(5, 15000);

			int batchSize = strategy.calculateBatchSize(sampleItems, 100);

			// Should be reduced to half
			assertThat(batchSize).isEqualTo(50);
		}

		@Test
		@DisplayName("Should return requested size for empty sample")
		void shouldReturnRequestedSizeForEmptySample() {
			List<JsonNode> sampleItems = new ArrayList<>();

			int batchSize = strategy.calculateBatchSize(sampleItems, 100);

			assertThat(batchSize).isEqualTo(100);
		}

		@Test
		@DisplayName("Should return at least 1 for very large items")
		void shouldReturnAtLeastOneForVeryLargeItems() {
			// Create very large items
			List<JsonNode> sampleItems = createLargeItems(5, 50000);

			// Request batch size of 2, half would be 1
			int batchSize = strategy.calculateBatchSize(sampleItems, 2);

			assertThat(batchSize).isGreaterThanOrEqualTo(1);
		}

		@Test
		@DisplayName("Should handle serialization exception gracefully")
		void shouldHandleSerializationException() {
			// Create an ObjectMapper with a custom serializer that always throws
			ObjectMapper failingMapper = ObjectMapperFactory.create();
			SimpleModule module = new SimpleModule();
			module.addSerializer(ObjectNode.class, new StdSerializer<ObjectNode>(ObjectNode.class) {
				@Override
				public void serialize(ObjectNode value, JsonGenerator gen, SerializerProvider provider)
						throws IOException {
					throw new IOException("Simulated serialization error");
				}
			});
			failingMapper.registerModule(module);

			AdaptiveBatchStrategy strategyWithFailingMapper = new AdaptiveBatchStrategy(failingMapper, 10000);

			List<JsonNode> sampleItems = createSmallItems(3);

			int batchSize = strategyWithFailingMapper.calculateBatchSize(sampleItems, 100);

			// Should return requested size on error
			assertThat(batchSize).isEqualTo(100);
		}

		@Test
		@DisplayName("Should use custom threshold")
		void shouldUseCustomThreshold() {
			// Create strategy with very low threshold (100 bytes)
			AdaptiveBatchStrategy lowThresholdStrategy = new AdaptiveBatchStrategy(objectMapper, 100);

			// Create items larger than 100 bytes but smaller than default 10000
			List<JsonNode> sampleItems = createLargeItems(5, 500);

			int batchSize = lowThresholdStrategy.calculateBatchSize(sampleItems, 100);

			// Should reduce batch size since items exceed low threshold
			assertThat(batchSize).isEqualTo(50);
		}

		@Test
		@DisplayName("Should not reduce batch size when items are at threshold boundary")
		void shouldNotReduceAtThresholdBoundary() {
			// Create items exactly at threshold
			AdaptiveBatchStrategy boundaryStrategy = new AdaptiveBatchStrategy(objectMapper, 1000);

			// Create items just below threshold
			List<JsonNode> sampleItems = createItemsOfApproximateSize(5, 900);

			int batchSize = boundaryStrategy.calculateBatchSize(sampleItems, 100);

			assertThat(batchSize).isEqualTo(100);
		}

	}

	@Nested
	@DisplayName("Constructor Tests")
	class ConstructorTest {

		@Test
		@DisplayName("Should store object mapper and threshold")
		void shouldStoreObjectMapperAndThreshold() {
			ObjectMapper mapper = ObjectMapperFactory.create();
			AdaptiveBatchStrategy customStrategy = new AdaptiveBatchStrategy(mapper, 5000);

			// Verify strategy works with provided components
			List<JsonNode> items = createLargeItems(5, 10000);

			int batchSize = customStrategy.calculateBatchSize(items, 100);

			// Should reduce since items (10000) > threshold (5000)
			assertThat(batchSize).isEqualTo(50);
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

	private List<JsonNode> createSmallItems(int count) {
		List<JsonNode> items = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("id", i);
			items.add(node);
		}
		return items;
	}

	private List<JsonNode> createLargeItems(int count, int approximateSize) {
		List<JsonNode> items = new ArrayList<>();
		// Create a string that will make each item approximately the target size
		String padding = "x".repeat(approximateSize);

		for (int i = 0; i < count; i++) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("id", i);
			node.put("data", padding);
			items.add(node);
		}
		return items;
	}

	private List<JsonNode> createItemsOfApproximateSize(int count, int targetSize) {
		List<JsonNode> items = new ArrayList<>();
		// Account for JSON overhead
		int paddingSize = Math.max(0, targetSize - 50);
		String padding = "x".repeat(paddingSize);

		for (int i = 0; i < count; i++) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("id", i);
			node.put("content", padding);
			items.add(node);
		}
		return items;
	}

}
