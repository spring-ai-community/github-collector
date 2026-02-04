package org.springaicommunity.github.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AdaptiveWindowPlanner Tests")
class AdaptiveWindowPlannerTest {

	@Nested
	@DisplayName("Construction")
	class ConstructionTest {

		@Test
		@DisplayName("Should create with default max per window")
		void shouldCreateWithDefaults() {
			var planner = new AdaptiveWindowPlanner();
			assertThat(planner).isNotNull();
		}

		@Test
		@DisplayName("Should create with custom max per window")
		void shouldCreateWithCustomMax() {
			var planner = new AdaptiveWindowPlanner(500);
			assertThat(planner).isNotNull();
		}

		@Test
		@DisplayName("Should reject non-positive max per window")
		void shouldRejectNonPositiveMax() {
			assertThatThrownBy(() -> new AdaptiveWindowPlanner(0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new AdaptiveWindowPlanner(-1)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	@DisplayName("Single window (no split needed)")
	class SingleWindowTest {

		@Test
		@DisplayName("Should return single window when count is under limit")
		void shouldReturnSingleWindow() {
			var planner = new AdaptiveWindowPlanner(900);
			BiFunction<String, String, Integer> count = (after, before) -> 500;

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2026-01-01", count);

			assertThat(windows).hasSize(1);
			assertThat(windows.get(0).createdAfter()).isEqualTo("2023-01-01");
			assertThat(windows.get(0).createdBefore()).isEqualTo("2026-01-01");
		}

		@Test
		@DisplayName("Should return single window when count equals limit")
		void shouldReturnSingleWindowAtLimit() {
			var planner = new AdaptiveWindowPlanner(900);
			BiFunction<String, String, Integer> count = (after, before) -> 900;

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2026-01-01", count);

			assertThat(windows).hasSize(1);
		}

		@Test
		@DisplayName("Should return single window when count is zero")
		void shouldReturnSingleWindowForZero() {
			var planner = new AdaptiveWindowPlanner(900);
			BiFunction<String, String, Integer> count = (after, before) -> 0;

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2023-06-01", count);

			assertThat(windows).hasSize(1);
		}

	}

	@Nested
	@DisplayName("Binary splitting")
	class SplitTest {

		@Test
		@DisplayName("Should split into two windows when count exceeds limit")
		void shouldSplitIntoTwo() {
			var planner = new AdaptiveWindowPlanner(900);

			// Full range has 1500 items; each half has 750
			BiFunction<String, String, Integer> count = (after, before) -> {
				if ("2023-01-01".equals(after) && "2024-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2024-01-01", count);

			assertThat(windows).hasSize(2);
			// First half
			assertThat(windows.get(0).createdAfter()).isEqualTo("2023-01-01");
			assertThat(windows.get(0).createdBefore()).isEqualTo("2023-07-02");
			// Second half
			assertThat(windows.get(1).createdAfter()).isEqualTo("2023-07-02");
			assertThat(windows.get(1).createdBefore()).isEqualTo("2024-01-01");
		}

		@Test
		@DisplayName("Should split recursively when halves still exceed limit")
		void shouldSplitRecursively() {
			var planner = new AdaptiveWindowPlanner(500);

			// Simulate: full=2000, each half=1000, each quarter=500
			BiFunction<String, String, Integer> count = (after, before) -> {
				var start = java.time.LocalDate.parse(after);
				var end = java.time.LocalDate.parse(before);
				long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
				// ~5.5 items per day over 365 days
				return (int) (days * 5.5);
			};

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2024-01-01", count);

			// Should split into 4 windows (each ~91 days * 5.5 = ~500)
			assertThat(windows).hasSizeGreaterThanOrEqualTo(4);

			// Windows should be contiguous
			for (int i = 1; i < windows.size(); i++) {
				assertThat(windows.get(i).createdAfter()).isEqualTo(windows.get(i - 1).createdBefore());
			}

			// Windows should cover the full range
			assertThat(windows.get(0).createdAfter()).isEqualTo("2023-01-01");
			assertThat(windows.get(windows.size() - 1).createdBefore()).isEqualTo("2024-01-01");
		}

	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCaseTest {

		@Test
		@DisplayName("Should handle 1-day range that exceeds limit")
		void shouldHandleOneDayRange() {
			var planner = new AdaptiveWindowPlanner(900);
			BiFunction<String, String, Integer> count = (after, before) -> 2000;

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-06-15", "2023-06-16", count);

			// Can't split further — returns oversized window
			assertThat(windows).hasSize(1);
			assertThat(windows.get(0).createdAfter()).isEqualTo("2023-06-15");
			assertThat(windows.get(0).createdBefore()).isEqualTo("2023-06-16");
		}

		@Test
		@DisplayName("Should handle count function returning -1 (error)")
		void shouldHandleCountError() {
			var planner = new AdaptiveWindowPlanner(900);
			BiFunction<String, String, Integer> count = (after, before) -> -1;

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-01-01", "2024-01-01", count);

			// Falls back to single window
			assertThat(windows).hasSize(1);
		}

		@Test
		@DisplayName("Should handle 2-day range with split")
		void shouldHandleTwoDayRange() {
			var planner = new AdaptiveWindowPlanner(100);

			BiFunction<String, String, Integer> count = (after, before) -> {
				if ("2023-06-15".equals(after) && "2023-06-17".equals(before)) {
					return 200;
				}
				return 80;
			};

			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2023-06-15", "2023-06-17", count);

			assertThat(windows).hasSize(2);
			assertThat(windows.get(0).createdAfter()).isEqualTo("2023-06-15");
			assertThat(windows.get(0).createdBefore()).isEqualTo("2023-06-16");
			assertThat(windows.get(1).createdAfter()).isEqualTo("2023-06-16");
			assertThat(windows.get(1).createdBefore()).isEqualTo("2023-06-17");
		}

	}

}
