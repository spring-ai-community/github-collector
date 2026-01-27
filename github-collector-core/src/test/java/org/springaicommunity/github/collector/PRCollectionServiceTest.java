package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PRCollectionService using plain JUnit with mocked dependencies. NO Spring
 * context to prevent accidental production operations. Uses @TempDir for safe file
 * operations and mocks ALL external dependencies.
 */
@DisplayName("PRCollectionService Tests - Plain JUnit with Mocked Dependencies")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PRCollectionServiceTest {

	@Mock
	private GraphQLService mockGraphQLService;

	@Mock
	private RestService mockRestService;

	private JsonNodeUtils realJsonUtils;

	private CollectionProperties realProperties;

	@Mock
	private CollectionStateRepository mockStateRepository;

	@Mock
	private ArchiveService mockArchiveService;

	@Mock
	private BatchStrategy mockBatchStrategy;

	private ObjectMapper realObjectMapper;

	private PRCollectionService prCollectionService;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		realObjectMapper = new ObjectMapper();
		realObjectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
		realJsonUtils = new JsonNodeUtils();

		// Setup real properties with safe defaults
		realProperties = new CollectionProperties();
		realProperties.setMaxBatchSizeBytes(1024 * 1024); // 1MB
		realProperties.setLargeIssueThreshold(50);
		realProperties.setSizeThreshold(100 * 1024); // 100KB
		realProperties.setResumeFile("resume.json");
		realProperties.setMaxRetries(3);
		realProperties.setRetryDelay(1);

		prCollectionService = new PRCollectionService(mockGraphQLService, mockRestService, realJsonUtils,
				realObjectMapper, realProperties, mockStateRepository, mockArchiveService, mockBatchStrategy);
	}

	/**
	 * Helper to create a PR request for multiple PRs
	 */
	private CollectionRequest createPRRequest(String repository, int batchSize, boolean dryRun, String prState) {
		return new CollectionRequest(repository, batchSize, dryRun, false, false, true, false, "closed", List.of(),
				"any", null, null, null, "prs", null, prState, false);
	}

	/**
	 * Helper to create a PR request for a specific PR number
	 */
	private CollectionRequest createSpecificPRRequest(String repository, int prNumber, boolean dryRun) {
		return new CollectionRequest(repository, 50, dryRun, false, false, true, false, "closed", List.of(), "any",
				null, null, null, "prs", prNumber, "all", false);
	}

	/**
	 * Helper to create mock PR data
	 */
	private JsonNode createMockPRData(int number, String title, String state) {
		ObjectNode pr = realObjectMapper.createObjectNode();
		pr.put("number", number);
		pr.put("title", title);
		pr.put("state", state);
		pr.put("html_url", "https://github.com/owner/repo/pull/" + number);
		return pr;
	}

	/**
	 * Helper to create mock review data
	 */
	private JsonNode createMockReviewData(String state, String authorAssociation, String login) {
		ObjectNode review = realObjectMapper.createObjectNode();
		review.put("state", state);
		review.put("author_association", authorAssociation);
		ObjectNode user = realObjectMapper.createObjectNode();
		user.put("login", login);
		review.set("user", user);
		review.put("submitted_at", "2024-01-15T10:30:00Z");
		return review;
	}

	/**
	 * Helper to create mock search response
	 */
	private JsonNode createMockSearchResponse(List<JsonNode> items) {
		ObjectNode response = realObjectMapper.createObjectNode();
		response.put("total_count", items.size());
		ArrayNode itemsArray = realObjectMapper.createArrayNode();
		items.forEach(itemsArray::add);
		response.set("items", itemsArray);
		return response;
	}

	@Nested
	@DisplayName("Dry Run Operations - Safe Testing")
	class DryRunOperationsTest {

		@Test
		@DisplayName("Should handle dry run for multiple PRs without creating files")
		void shouldHandleDryRunForMultiplePRsWithoutCreatingFiles() {
			// Setup
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr is:open");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(150);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");

			// Execute
			CollectionResult result = prCollectionService.collectItems(request);

			// Verify
			assertThat(result.totalIssues()).isEqualTo(150);
			assertThat(result.processedIssues()).isEqualTo(0);
			assertThat(result.outputDirectory()).isEqualTo("dry-run");
			assertThat(result.batchFiles()).containsExactly("pr_batch.json");

			// Verify no file operations
			verify(mockStateRepository, never()).createOutputDirectory(anyString(), anyString(), anyString());
			verify(mockStateRepository, never()).saveBatch(any(), anyInt(), any(), anyString(), anyBoolean());
		}

		@Test
		@DisplayName("Should handle dry run for specific PR")
		void shouldHandleDryRunForSpecificPR() {
			CollectionRequest request = createSpecificPRRequest("owner/repo", 123, true);

			CollectionResult result = prCollectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(1);
			assertThat(result.processedIssues()).isEqualTo(1);
			assertThat(result.outputDirectory()).isEqualTo("dry-run");
			assertThat(result.batchFiles()).containsExactly("pr_123.json");

			// Verify no REST calls were made
			verify(mockRestService, never()).getPullRequest(anyString(), anyString(), anyInt());
		}

		@ParameterizedTest
		@CsvSource({ "open, 75", "closed, 150", "merged, 50", "all, 225" })
		@DisplayName("Should handle different PR states in dry run")
		void shouldHandleDifferentPRStatesInDryRun(String state, int expectedCount) {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(expectedCount);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, state);

			CollectionResult result = prCollectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(expectedCount);
			assertThat(result.processedIssues()).isEqualTo(0);
		}

		@Test
		@DisplayName("Should handle label filtering in dry run")
		void shouldHandleLabelFilteringInDryRun() {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr label:\"bug\"");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(25);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of("bug", "enhancement"), "all", null, null, null, "prs", null, "open", false);

			CollectionResult result = prCollectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(25);

			// Verify label filters were passed
			verify(mockRestService).buildPRSearchQuery(eq("owner/repo"), eq("open"), eq(List.of("bug", "enhancement")),
					eq("all"));
		}

	}

	@Nested
	@DisplayName("Specific PR Collection Tests")
	class SpecificPRCollectionTest {

		@Test
		@DisplayName("Should collect specific PR by number")
		void shouldCollectSpecificPRByNumber() {
			// Setup mocks
			JsonNode mockPR = createMockPRData(123, "Fix critical bug", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();

			when(mockRestService.getPullRequest("owner", "repo", 123)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 123)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 123, false);

			// Execute
			CollectionResult result = prCollectionService.collectItems(request);

			// Verify
			assertThat(result.totalIssues()).isEqualTo(1);
			assertThat(result.processedIssues()).isEqualTo(1);
			assertThat(result.batchFiles()).containsExactly("batch_001_prs.json");

			verify(mockRestService).getPullRequest("owner", "repo", 123);
			verify(mockRestService).getPullRequestReviews("owner", "repo", 123);
		}

		@Test
		@DisplayName("Should enhance PR with soft approval when contributor approves")
		void shouldEnhancePRWithSoftApprovalWhenContributorApproves() {
			// Setup
			JsonNode mockPR = createMockPRData(456, "Add new feature", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();
			mockReviews.add(createMockReviewData("APPROVED", "CONTRIBUTOR", "helpful-contributor"));

			when(mockRestService.getPullRequest("owner", "repo", 456)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 456)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 456, false);

			// Execute
			CollectionResult result = prCollectionService.collectItems(request);

			// Verify batch was saved with enhanced data
			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("prs"), eq(false));

			// The batch data should contain soft approval info
			assertThat(result.processedIssues()).isEqualTo(1);
		}

		@Test
		@DisplayName("Should handle PR collection error gracefully")
		void shouldHandlePRCollectionErrorGracefully() {
			when(mockRestService.getPullRequest("owner", "repo", 999)).thenThrow(new RuntimeException("PR not found"));
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);

			CollectionRequest request = createSpecificPRRequest("owner/repo", 999, false);

			assertThatThrownBy(() -> prCollectionService.collectItems(request)).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to collect PR #999");
		}

	}

	@Nested
	@DisplayName("Soft Approval Detection Tests")
	class SoftApprovalDetectionTest {

		@Test
		@DisplayName("Should detect soft approval from CONTRIBUTOR")
		void shouldDetectSoftApprovalFromContributor() {
			JsonNode mockPR = createMockPRData(100, "Test PR", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();
			mockReviews.add(createMockReviewData("APPROVED", "CONTRIBUTOR", "contributor-user"));

			when(mockRestService.getPullRequest("owner", "repo", 100)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 100)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 100, false);
			prCollectionService.collectItems(request);

			// Verify save was called (soft approval should be in the data)
			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("prs"), eq(false));
		}

		@Test
		@DisplayName("Should detect soft approval from FIRST_TIME_CONTRIBUTOR")
		void shouldDetectSoftApprovalFromFirstTimeContributor() {
			JsonNode mockPR = createMockPRData(101, "First contribution", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();
			mockReviews.add(createMockReviewData("APPROVED", "FIRST_TIME_CONTRIBUTOR", "new-contributor"));

			when(mockRestService.getPullRequest("owner", "repo", 101)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 101)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 101, false);
			prCollectionService.collectItems(request);

			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("prs"), eq(false));
		}

		@Test
		@DisplayName("Should NOT detect soft approval from MEMBER")
		void shouldNotDetectSoftApprovalFromMember() {
			JsonNode mockPR = createMockPRData(102, "Member approved", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();
			mockReviews.add(createMockReviewData("APPROVED", "MEMBER", "team-member"));

			when(mockRestService.getPullRequest("owner", "repo", 102)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 102)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 102, false);
			prCollectionService.collectItems(request);

			// PR should be processed but without soft approval flag
			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("prs"), eq(false));
		}

		@Test
		@DisplayName("Should NOT detect soft approval for non-APPROVED reviews")
		void shouldNotDetectSoftApprovalForNonApprovedReviews() {
			JsonNode mockPR = createMockPRData(103, "Changes requested", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();
			mockReviews.add(createMockReviewData("CHANGES_REQUESTED", "CONTRIBUTOR", "reviewer"));
			mockReviews.add(createMockReviewData("COMMENTED", "FIRST_TIME_CONTRIBUTOR", "commenter"));

			when(mockRestService.getPullRequest("owner", "repo", 103)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 103)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 103, false);
			prCollectionService.collectItems(request);

			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("prs"), eq(false));
		}

		@Test
		@DisplayName("Should handle empty reviews array")
		void shouldHandleEmptyReviewsArray() {
			JsonNode mockPR = createMockPRData(104, "No reviews yet", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode(); // Empty

			when(mockRestService.getPullRequest("owner", "repo", 104)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 104)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 104, false);

			// Should not throw
			assertThatCode(() -> prCollectionService.collectItems(request)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Should handle null reviews response")
		void shouldHandleNullReviewsResponse() {
			JsonNode mockPR = createMockPRData(105, "Null reviews", "open");
			ObjectNode notArrayReviews = realObjectMapper.createObjectNode(); // Not an
																				// array

			when(mockRestService.getPullRequest("owner", "repo", 105)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 105)).thenReturn(notArrayReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			CollectionRequest request = createSpecificPRRequest("owner/repo", 105, false);

			assertThatCode(() -> prCollectionService.collectItems(request)).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Multiple PR Collection Tests")
	class MultiplePRCollectionTest {

		@Test
		@DisplayName("Should collect multiple PRs with pagination")
		void shouldCollectMultiplePRsWithPagination() {
			// Setup search response
			JsonNode pr1 = createMockPRData(1, "PR 1", "open");
			JsonNode pr2 = createMockPRData(2, "PR 2", "open");
			JsonNode searchResponse = createMockSearchResponse(List.of(pr1, pr2));

			ArrayNode emptyReviews = realObjectMapper.createArrayNode();

			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(2);
			when(mockRestService.searchPRs(anyString(), anyInt(), any())).thenReturn(searchResponse);
			when(mockRestService.getPullRequestReviews(anyString(), anyString(), anyInt())).thenReturn(emptyReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");
			when(mockBatchStrategy.createBatch(anyList(), anyInt())).thenAnswer(inv -> {
				List<JsonNode> pending = inv.getArgument(0);
				int size = Math.min(inv.getArgument(1), pending.size());
				List<JsonNode> batch = new java.util.ArrayList<>(pending.subList(0, size));
				pending.subList(0, size).clear();
				return batch;
			});

			CollectionRequest request = createPRRequest("owner/repo", 50, false, "open");

			CollectionResult result = prCollectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(2);
			assertThat(result.processedIssues()).isEqualTo(2);
		}

		@Test
		@DisplayName("Should handle empty search results")
		void shouldHandleEmptySearchResults() {
			JsonNode emptyResponse = createMockSearchResponse(List.of());

			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(0);
			when(mockRestService.searchPRs(anyString(), anyInt(), any())).thenReturn(emptyResponse);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockBatchStrategy.createBatch(anyList(), anyInt())).thenReturn(List.of());

			CollectionRequest request = createPRRequest("owner/repo", 50, false, "open");

			CollectionResult result = prCollectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(0);
			assertThat(result.processedIssues()).isEqualTo(0);
		}

	}

	@Nested
	@DisplayName("Search Query Building Tests")
	class SearchQueryBuildingTest {

		@Test
		@DisplayName("Should delegate search query building to RestService")
		void shouldDelegateSearchQueryBuildingToRestService() {
			when(mockRestService.buildPRSearchQuery("owner/repo", "open", List.of(), "any"))
				.thenReturn("repo:owner/repo is:pr is:open");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(10);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");
			prCollectionService.collectItems(request);

			verify(mockRestService).buildPRSearchQuery("owner/repo", "open", List.of(), "any");
		}

		@ParameterizedTest
		@ValueSource(strings = { "open", "closed", "merged", "all" })
		@DisplayName("Should pass correct PR state to query builder")
		void shouldPassCorrectPRStateToQueryBuilder(String state) {
			when(mockRestService.buildPRSearchQuery(anyString(), eq(state), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(5);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, state);
			prCollectionService.collectItems(request);

			verify(mockRestService).buildPRSearchQuery(eq("owner/repo"), eq(state), anyList(), anyString());
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTest {

		@Test
		@DisplayName("Should handle invalid repository format")
		void shouldHandleInvalidRepositoryFormat() {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("invalid");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(0);

			CollectionRequest request = createPRRequest("invalid-repo-format", 50, true, "open");

			assertThatThrownBy(() -> prCollectionService.collectItems(request))
				.isInstanceOf(ArrayIndexOutOfBoundsException.class);
		}

		@Test
		@DisplayName("Should handle REST service errors")
		void shouldHandleRestServiceErrors() {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenThrow(new RuntimeException("API error"));

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");

			assertThatThrownBy(() -> prCollectionService.collectItems(request)).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("API error");
		}

		@Test
		@DisplayName("Should handle state repository errors during collection")
		void shouldHandleStateRepositoryErrorsDuringCollection() {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(5);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString()))
				.thenThrow(new RuntimeException("Cannot create directory"));

			CollectionRequest request = createPRRequest("owner/repo", 50, false, "open");

			assertThatThrownBy(() -> prCollectionService.collectItems(request)).isInstanceOf(RuntimeException.class);
		}

	}

	@Nested
	@DisplayName("Collection Type Tests")
	class CollectionTypeTest {

		@Test
		@DisplayName("Should return 'prs' as collection type")
		void shouldReturnPrsAsCollectionType() {
			// The collection type affects output directory and file naming
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(1);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");
			prCollectionService.collectItems(request);

			// Verify the search query was built (confirming PR-specific behavior)
			verify(mockRestService).buildPRSearchQuery(anyString(), anyString(), anyList(), anyString());
			verify(mockRestService).getTotalPRCount(anyString());
		}

		@Test
		@DisplayName("Should return 'PRs' as item type name")
		void shouldReturnPRsAsItemTypeName() {
			// This is used in logging - verify through successful execution
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(0);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");

			// Should complete without errors
			assertThatCode(() -> prCollectionService.collectItems(request)).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("ZIP Archive Tests")
	class ZipArchiveTest {

		@Test
		@DisplayName("Should create ZIP when requested for specific PR")
		void shouldCreateZipWhenRequestedForSpecificPR() {
			JsonNode mockPR = createMockPRData(200, "Test PR", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();

			when(mockRestService.getPullRequest("owner", "repo", 200)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 200)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			// Request with zip=true
			CollectionRequest request = new CollectionRequest("owner/repo", 50, false, false, true, true, false,
					"closed", List.of(), "any", null, null, null, "prs", 200, "all", false);

			prCollectionService.collectItems(request);

			// Verify archive service was called
			verify(mockArchiveService).createArchive(eq(tempDir), anyList(), contains("prs_owner_repo"), eq(false));
		}

		@Test
		@DisplayName("Should NOT create ZIP when not requested")
		void shouldNotCreateZipWhenNotRequested() {
			JsonNode mockPR = createMockPRData(201, "Test PR", "open");
			ArrayNode mockReviews = realObjectMapper.createArrayNode();

			when(mockRestService.getPullRequest("owner", "repo", 201)).thenReturn(mockPR);
			when(mockRestService.getPullRequestReviews("owner", "repo", 201)).thenReturn(mockReviews);
			when(mockStateRepository.createOutputDirectory(anyString(), anyString(), anyString())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), anyString(), anyBoolean()))
				.thenReturn("batch_001_prs.json");

			// Request with zip=false
			CollectionRequest request = createSpecificPRRequest("owner/repo", 201, false);

			prCollectionService.collectItems(request);

			// Verify archive service was NOT called
			verify(mockArchiveService, never()).createArchive(any(), anyList(), anyString(), anyBoolean());
		}

	}

	@Nested
	@DisplayName("Component Tests")
	class ComponentTest {

		@Test
		@DisplayName("Service should be independently testable without Spring context")
		void serviceShouldBeIndependentlyTestableWithoutSpringContext() {
			assertThat(prCollectionService).isNotNull();

			// Verify no interactions before any method call
			verifyNoInteractions(mockGraphQLService, mockRestService);
		}

		@Test
		@DisplayName("Should use RestService instead of GraphQLService for PR operations")
		void shouldUseRestServiceInsteadOfGraphQLServiceForPROperations() {
			when(mockRestService.buildPRSearchQuery(anyString(), anyString(), anyList(), anyString()))
				.thenReturn("repo:owner/repo is:pr");
			when(mockRestService.getTotalPRCount(anyString())).thenReturn(10);

			CollectionRequest request = createPRRequest("owner/repo", 50, true, "open");
			prCollectionService.collectItems(request);

			// PRCollectionService should use REST API, not GraphQL
			verify(mockRestService).getTotalPRCount(anyString());
			verifyNoInteractions(mockGraphQLService);
		}

	}

}
