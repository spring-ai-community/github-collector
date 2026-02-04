package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the adaptive time-window collection pipeline.
 *
 * <p>
 * Tests the full flow: WindowedCollectionService -> AdaptiveWindowPlanner ->
 * IssueCollectionService/PRCollectionService, with mocked GitHub API services but real
 * orchestration logic.
 *
 * <p>
 * These tests verify:
 * <ul>
 * <li>Planner correctly splits based on count function</li>
 * <li>Delegate receives correct date ranges per window</li>
 * <li>Batch offset is applied for continuous numbering</li>
 * <li>Clean flag is only set for the first window</li>
 * <li>Results are correctly merged across windows</li>
 * </ul>
 */
@DisplayName("Windowed Collection Integration Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WindowedCollectionIntegrationTest {

	@Mock
	private GraphQLService mockGraphQLService;

	@Mock
	private RestService mockRestService;

	@Mock
	private CollectionStateRepository mockStateRepository;

	@Mock
	private ArchiveService mockArchiveService;

	private CollectionProperties properties;

	private ObjectMapper objectMapper;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		objectMapper = ObjectMapperFactory.create();
		properties = new CollectionProperties();
		properties.setBatchSize(100);
		properties.setMaxRetries(1);
		properties.setRetryDelay(1);
	}

	private Issue createIssue(int number) {
		return new Issue(number, "Issue #" + number, "Body", "CLOSED", LocalDateTime.of(2024, 6, 15, 10, 0),
				LocalDateTime.of(2024, 6, 15, 12, 0), LocalDateTime.of(2024, 6, 16, 10, 0),
				"https://api.github.com/repos/owner/repo/issues/" + number, new Author("author", "Author"), List.of(),
				List.of(), List.of());
	}

	@Nested
	@DisplayName("Windowed issue collection with real services")
	class WindowedIssueCollectionTest {

		private IssueCollectionService issueService;

		@BeforeEach
		void setUpIssueService() {
			BatchStrategy<Issue> batchStrategy = new FixedBatchStrategy<>();
			issueService = new IssueCollectionService(mockGraphQLService, mockRestService, objectMapper, properties,
					mockStateRepository, mockArchiveService, batchStrategy);
		}

		@Test
		@DisplayName("Should collect across two windows with correct batch offsets")
		void shouldCollectAcrossTwoWindowsWithBatchOffsets() {
			// Count function: full range has 1500, each half has 750
			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			var planner = new AdaptiveWindowPlanner(900);
			var windowed = new WindowedCollectionService<>(issueService, planner, countFn);

			// Mock issue count for each window's search query
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(3);

			// Mock search results: 3 issues per window
			List<Issue> window1Issues = List.of(createIssue(1), createIssue(2), createIssue(3));
			List<Issue> window2Issues = List.of(createIssue(4), createIssue(5), createIssue(6));

			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any())).thenReturn(
					new SearchResult<>(window1Issues, null, false), new SearchResult<>(window2Issues, null, false));

			// Mock event fetching (returns empty events for each issue)
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());

			// Mock state repository
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);

			// Capture batch saves to verify numbering
			List<Integer> batchIndices = new ArrayList<>();
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean())).thenAnswer(inv -> {
				int batchIndex = inv.getArgument(1);
				batchIndices.add(batchIndex);
				return "batch_" + String.format("%03d", batchIndex) + "_issues.json";
			});

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2023-01-01")
				.createdBefore("2026-01-01")
				.clean(true)
				.build();

			CollectionResult result = windowed.collectItems(request);

			// Verify merged result
			assertThat(result.totalIssues()).isEqualTo(6);
			assertThat(result.processedIssues()).isEqualTo(6);
			assertThat(result.batchFiles()).hasSize(2);

			// Verify batch numbering: first window starts at 1, second starts offset
			assertThat(batchIndices).hasSize(2);
			assertThat(batchIndices.get(0)).isEqualTo(1); // First window, first batch
			assertThat(batchIndices.get(1)).isEqualTo(2); // Second window, offset by 1
		}

		@Test
		@DisplayName("Should pass correct date ranges to delegate")
		void shouldPassCorrectDateRangesToDelegate() {
			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			var planner = new AdaptiveWindowPlanner(900);
			var windowed = new WindowedCollectionService<>(issueService, planner, countFn);

			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(2);
			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(List.of(createIssue(1), createIssue(2)), null, false));
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch.json");

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2023-01-01")
				.createdBefore("2026-01-01")
				.clean(true)
				.build();

			windowed.collectItems(request);

			// Capture the search queries to verify date ranges
			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService, atLeast(2)).getSearchIssueCount(queryCaptor.capture());

			List<String> queries = queryCaptor.getAllValues();
			// The planner queries the full range first, then the two halves
			// The delegate then queries each half again for its own count
			boolean hasFirstHalf = queries.stream().anyMatch(q -> q.contains("created:2023-01-01.."));
			boolean hasSecondHalf = queries.stream().anyMatch(q -> q.contains("..2026-01-01"));
			assertThat(hasFirstHalf).isTrue();
			assertThat(hasSecondHalf).isTrue();
		}

		@Test
		@DisplayName("Should only clean on first window")
		void shouldOnlyCleanOnFirstWindow() {
			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			var planner = new AdaptiveWindowPlanner(900);
			var windowed = new WindowedCollectionService<>(issueService, planner, countFn);

			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(1);
			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(List.of(createIssue(1)), null, false));
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch.json");

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2023-01-01")
				.createdBefore("2026-01-01")
				.clean(true)
				.build();

			windowed.collectItems(request);

			// Verify cleanOutputDirectory called only once — the first window has
			// clean=true so it calls stateRepository.cleanOutputDirectory(), but the
			// second window has clean=false so the guard in BaseCollectionService
			// returns early without calling the repository.
			verify(mockStateRepository, times(1)).cleanOutputDirectory(any());
		}

		@Test
		@DisplayName("Should pass through without windowing when count fits")
		void shouldPassThroughWhenCountFits() {
			BiFunction<String, String, Integer> countFn = (after, before) -> 500;

			var planner = new AdaptiveWindowPlanner(900);
			var windowed = new WindowedCollectionService<>(issueService, planner, countFn);

			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(2);
			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(List.of(createIssue(1), createIssue(2)), null, false));
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch.json");

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2023-01-01")
				.createdBefore("2026-01-01")
				.clean(true)
				.build();

			CollectionResult result = windowed.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(2);
			assertThat(result.processedIssues()).isEqualTo(2);
		}

	}

	@Nested
	@DisplayName("Windowed PR collection with real services")
	class WindowedPRCollectionTest {

		private PRCollectionService prService;

		@BeforeEach
		void setUpPRService() {
			BatchStrategy<AnalyzedPullRequest> batchStrategy = new FixedBatchStrategy<>();
			prService = new PRCollectionService(mockGraphQLService, mockRestService, objectMapper, properties,
					mockStateRepository, mockArchiveService, batchStrategy);
		}

		private PullRequest createPR(int number) {
			return new PullRequest(number, "PR #" + number, "Body", "CLOSED", LocalDateTime.of(2024, 6, 15, 10, 0),
					LocalDateTime.of(2024, 6, 15, 12, 0), LocalDateTime.of(2024, 6, 16, 10, 0), null,
					"https://api.github.com/repos/owner/repo/pulls/" + number,
					"https://github.com/owner/repo/pull/" + number, new Author("author", "Author"), List.of(),
					List.of(), List.of(), false, false, null, "feature", "main", 10, 5, 2);
		}

		@Test
		@DisplayName("Should collect PRs across two windows with batch offsets")
		void shouldCollectPRsAcrossTwoWindows() {
			BiFunction<String, String, Integer> countFn = (after, before) -> {
				if ("2023-01-01".equals(after) && "2026-01-01".equals(before)) {
					return 1500;
				}
				return 750;
			};

			var planner = new AdaptiveWindowPlanner(900);
			var windowed = new WindowedCollectionService<>(prService, planner, countFn);

			// Mock PR query building and counting
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString(), any(), any()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(2);

			// Mock search results for each window
			List<PullRequest> window1PRs = List.of(createPR(10), createPR(11));
			List<PullRequest> window2PRs = List.of(createPR(20), createPR(21));

			when(mockRestService.searchPRs(anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(window1PRs, null, false), new SearchResult<>(window2PRs, null, false));

			// Mock review fetching
			when(mockRestService.getPullRequestReviews(anyString(), anyString(), anyInt())).thenReturn(List.of());

			// Mock state repository
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);

			List<Integer> batchIndices = new ArrayList<>();
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean())).thenAnswer(inv -> {
				int batchIndex = inv.getArgument(1);
				batchIndices.add(batchIndex);
				return "batch_" + String.format("%03d", batchIndex) + "_prs.json";
			});

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.prState("closed")
				.collectionType("prs")
				.createdAfter("2023-01-01")
				.createdBefore("2026-01-01")
				.clean(true)
				.build();

			CollectionResult result = windowed.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(4);
			assertThat(result.processedIssues()).isEqualTo(4);
			assertThat(result.batchFiles()).hasSize(2);

			// Batch numbering should be continuous
			assertThat(batchIndices.get(0)).isEqualTo(1);
			assertThat(batchIndices.get(1)).isEqualTo(2);
		}

	}

	@Nested
	@DisplayName("batchOffset behavior in collectItemsInBatches")
	class BatchOffsetTest {

		private IssueCollectionService issueService;

		@BeforeEach
		void setUpIssueService() {
			BatchStrategy<Issue> batchStrategy = new FixedBatchStrategy<>();
			issueService = new IssueCollectionService(mockGraphQLService, mockRestService, objectMapper, properties,
					mockStateRepository, mockArchiveService, batchStrategy);
		}

		@Test
		@DisplayName("Should start batch numbering at batchOffset + 1")
		void shouldStartAtBatchOffset() {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(2);
			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(List.of(createIssue(1), createIssue(2)), null, false));
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);

			List<Integer> batchIndices = new ArrayList<>();
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean())).thenAnswer(inv -> {
				batchIndices.add(inv.getArgument(1));
				return "batch.json";
			});

			// Request with batchOffset=5 — batches should start at 6
			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2024-01-01")
				.createdBefore("2024-06-01")
				.batchOffset(5)
				.clean(false)
				.build();

			issueService.collectItems(request);

			assertThat(batchIndices).hasSize(1);
			assertThat(batchIndices.get(0)).isEqualTo(6); // 5 + 1
		}

		@Test
		@DisplayName("Should start at 1 when batchOffset is null")
		void shouldStartAtOneWithoutOffset() {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(2);
			when(mockGraphQLService.searchIssues(anyString(), anyString(), anyString(), anyInt(), any()))
				.thenReturn(new SearchResult<>(List.of(createIssue(1), createIssue(2)), null, false));
			when(mockRestService.getIssueEvents(anyString(), anyString(), anyInt())).thenReturn(List.of());
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);

			List<Integer> batchIndices = new ArrayList<>();
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean())).thenAnswer(inv -> {
				batchIndices.add(inv.getArgument(1));
				return "batch.json";
			});

			CollectionRequest request = CollectionRequest.builder()
				.repository("owner/repo")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2024-01-01")
				.createdBefore("2024-06-01")
				.clean(true)
				.build();

			issueService.collectItems(request);

			assertThat(batchIndices).hasSize(1);
			assertThat(batchIndices.get(0)).isEqualTo(1);
		}

	}

	@Nested
	@DisplayName("GitHubCollectorBuilder windowed methods")
	class BuilderTest {

		@Test
		@DisplayName("Should build issue search query with date range")
		void shouldBuildIssueSearchQueryWithDates() {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("spring-projects/spring-boot", "closed",
					List.of(), "any", "2023-01-01", "2026-01-01");

			assertThat(query).contains("repo:spring-projects/spring-boot");
			assertThat(query).contains("is:issue");
			assertThat(query).contains("is:closed");
			assertThat(query).contains("created:2023-01-01..2026-01-01");
		}

		@Test
		@DisplayName("Should build issue search query without dates")
		void shouldBuildIssueSearchQueryWithoutDates() {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("owner/repo", "open", List.of(), "any", null,
					null);

			assertThat(query).contains("repo:owner/repo");
			assertThat(query).contains("is:issue");
			assertThat(query).contains("is:open");
			assertThat(query).doesNotContain("created:");
		}

		@Test
		@DisplayName("Should build issue search query with labels")
		void shouldBuildIssueSearchQueryWithLabels() {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("owner/repo", "closed",
					List.of("type: bug", "status: confirmed"), "all", "2024-01-01", "2025-01-01");

			assertThat(query).contains("label:\"type: bug\"");
			assertThat(query).contains("label:\"status: confirmed\"");
			assertThat(query).contains("created:2024-01-01..2025-01-01");
		}

		@Test
		@DisplayName("Should build issue search query with only createdAfter")
		void shouldBuildQueryWithOnlyAfter() {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("owner/repo", "all", List.of(), "any",
					"2024-01-01", null);

			assertThat(query).contains("created:>=2024-01-01");
			assertThat(query).doesNotContain("..");
		}

		@Test
		@DisplayName("Should build issue search query with only createdBefore")
		void shouldBuildQueryWithOnlyBefore() {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("owner/repo", "all", List.of(), "any", null,
					"2025-01-01");

			assertThat(query).contains("created:<2025-01-01");
			assertThat(query).doesNotContain("..");
		}

	}

}
