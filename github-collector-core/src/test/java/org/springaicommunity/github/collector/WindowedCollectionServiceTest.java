package org.springaicommunity.github.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WindowedCollectionService Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WindowedCollectionServiceTest {

	@Mock
	private BaseCollectionService<Issue> mockDelegate;

	private AdaptiveWindowPlanner planner;

	@BeforeEach
	void setUp() {
		planner = new AdaptiveWindowPlanner(900);
	}

	private CollectionRequest baseRequest() {
		return CollectionRequest.builder()
			.repository("spring-projects/spring-boot")
			.batchSize(100)
			.issueState("closed")
			.collectionType("issues")
			.createdAfter("2023-01-01")
			.createdBefore("2026-01-01")
			.clean(true)
			.build();
	}

	@Nested
	@DisplayName("Passthrough behavior")
	class PassthroughTest {

		@Test
		@DisplayName("Should pass through when no date range is set")
		void shouldPassThroughWithoutDates() {
			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.build();

			CollectionResult expected = new CollectionResult(500, 500, "/output", List.of("batch_1.json"));
			when(mockDelegate.collectItems(request)).thenReturn(expected);

			BiFunction<String, String, Integer> countFn = (a, b) -> 500;
			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);

			CollectionResult result = service.collectItems(request);

			assertThat(result).isEqualTo(expected);
			verify(mockDelegate).collectItems(request);
		}

		@Test
		@DisplayName("Should pass through when only createdAfter is set")
		void shouldPassThroughWithOnlyAfter() {
			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.createdAfter("2023-01-01")
				.build();

			CollectionResult expected = new CollectionResult(500, 500, "/output", List.of("batch_1.json"));
			when(mockDelegate.collectItems(request)).thenReturn(expected);

			BiFunction<String, String, Integer> countFn = (a, b) -> 500;
			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);

			CollectionResult result = service.collectItems(request);

			assertThat(result).isEqualTo(expected);
		}

		@Test
		@DisplayName("Should pass through when count fits in single window")
		void shouldPassThroughWhenCountUnderLimit() {
			CollectionRequest request = baseRequest();

			CollectionResult expected = new CollectionResult(500, 500, "/output", List.of("batch_1.json"));
			when(mockDelegate.collectItems(any())).thenReturn(expected);

			BiFunction<String, String, Integer> countFn = (a, b) -> 500;
			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);

			CollectionResult result = service.collectItems(request);

			assertThat(result).isEqualTo(expected);
			verify(mockDelegate).collectItems(any());
		}

	}

	@Nested
	@DisplayName("Windowed collection")
	class WindowedTest {

		@Test
		@DisplayName("Should split and collect across multiple windows")
		void shouldSplitAndCollect() {
			CollectionRequest request = baseRequest();

			// Count function: full range exceeds limit, each half fits
			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			// Mock delegate to return results for each window
			when(mockDelegate.collectItems(any())).thenReturn(
					new CollectionResult(750, 750, "/output", List.of("batch_1.json", "batch_2.json")),
					new CollectionResult(750, 750, "/output", List.of("batch_3.json", "batch_4.json")));

			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);
			CollectionResult result = service.collectItems(request);

			// Verify merged result
			assertThat(result.totalIssues()).isEqualTo(1500);
			assertThat(result.processedIssues()).isEqualTo(1500);
			assertThat(result.batchFiles()).hasSize(4);
			assertThat(result.outputDirectory()).isEqualTo("/output");

			// Verify delegate was called twice
			verify(mockDelegate, times(2)).collectItems(any());
		}

		@Test
		@DisplayName("Should set batchOffset for subsequent windows")
		void shouldSetBatchOffset() {
			CollectionRequest request = baseRequest();

			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			when(mockDelegate.collectItems(any())).thenReturn(
					new CollectionResult(750, 750, "/output", List.of("batch_1.json", "batch_2.json", "batch_3.json")),
					new CollectionResult(750, 750, "/output", List.of("batch_4.json", "batch_5.json")));

			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);
			service.collectItems(request);

			// Capture the requests passed to the delegate
			ArgumentCaptor<CollectionRequest> captor = ArgumentCaptor.forClass(CollectionRequest.class);
			verify(mockDelegate, times(2)).collectItems(captor.capture());

			List<CollectionRequest> requests = captor.getAllValues();

			// First window: no offset
			assertThat(requests.get(0).batchOffset()).isNull();

			// Second window: offset by number of batches from first window
			assertThat(requests.get(1).batchOffset()).isEqualTo(3);
		}

		@Test
		@DisplayName("Should only clean output directory on first window")
		void shouldOnlyCleanFirstWindow() {
			CollectionRequest request = baseRequest();
			assertThat(request.clean()).isTrue();

			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			when(mockDelegate.collectItems(any())).thenReturn(
					new CollectionResult(750, 750, "/output", List.of("batch_1.json")),
					new CollectionResult(750, 750, "/output", List.of("batch_2.json")));

			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);
			service.collectItems(request);

			ArgumentCaptor<CollectionRequest> captor = ArgumentCaptor.forClass(CollectionRequest.class);
			verify(mockDelegate, times(2)).collectItems(captor.capture());

			List<CollectionRequest> requests = captor.getAllValues();
			assertThat(requests.get(0).clean()).isTrue();
			assertThat(requests.get(1).clean()).isFalse();
		}

		@Test
		@DisplayName("Should set correct date ranges per window")
		void shouldSetCorrectDateRanges() {
			CollectionRequest request = baseRequest();

			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			when(mockDelegate.collectItems(any())).thenReturn(
					new CollectionResult(750, 750, "/output", List.of("batch_1.json")),
					new CollectionResult(750, 750, "/output", List.of("batch_2.json")));

			var service = new WindowedCollectionService<>(mockDelegate, planner, countFn);
			service.collectItems(request);

			ArgumentCaptor<CollectionRequest> captor = ArgumentCaptor.forClass(CollectionRequest.class);
			verify(mockDelegate, times(2)).collectItems(captor.capture());

			List<CollectionRequest> requests = captor.getAllValues();

			// First window: 2023-01-01 to midpoint
			assertThat(requests.get(0).createdAfter()).isEqualTo("2023-01-01");
			assertThat(requests.get(0).createdBefore()).isNotEqualTo("2026-01-01");

			// Second window: midpoint to 2026-01-01
			assertThat(requests.get(1).createdAfter()).isEqualTo(requests.get(0).createdBefore());
			assertThat(requests.get(1).createdBefore()).isEqualTo("2026-01-01");
		}

	}

}
