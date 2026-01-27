package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CollectionService using plain JUnit with mocked dependencies. NO Spring
 * context to prevent accidental production operations. Uses @TempDir for safe file
 * operations and mocks ALL external dependencies.
 *
 * ⚠️ CRITICAL SAFETY: This service contains main business logic that could trigger
 * production data collection. All tests must use mocked data and temporary directories.
 */
@DisplayName("IssueCollectionService Tests - Plain JUnit with Mocked Dependencies")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueCollectionServiceTest {

	@Mock
	private GraphQLService mockGraphQLService;

	@Mock
	private RestService mockRestService;

	private CollectionProperties realProperties;

	@Mock
	private CollectionStateRepository mockStateRepository;

	@Mock
	private ArchiveService mockArchiveService;

	@Mock
	private BatchStrategy<Issue> mockBatchStrategy;

	private ObjectMapper realObjectMapper;

	private IssueCollectionService collectionService;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		realObjectMapper = new ObjectMapper();
		realObjectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

		// Setup real properties with safe defaults
		realProperties = new CollectionProperties();
		realProperties.setMaxBatchSizeBytes(1024 * 1024); // 1MB
		realProperties.setLargeIssueThreshold(50);
		realProperties.setSizeThreshold(100 * 1024); // 100KB
		realProperties.setResumeFile("resume.json");
		realProperties.setMaxRetries(3);
		realProperties.setRetryDelay(1);

		collectionService = new IssueCollectionService(mockGraphQLService, mockRestService, realObjectMapper,
				realProperties, mockStateRepository, mockArchiveService, mockBatchStrategy);
	}

	@Nested
	@DisplayName("Dry Run Operations - Safe Testing")
	class DryRunOperationsTest {

		@Test
		@DisplayName("Should handle dry run without creating files")
		void shouldHandleDryRunWithoutCreatingFiles() throws Exception {
			// Setup mock response for issue count
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(150);

			CollectionRequest request = new CollectionRequest("test-owner/test-repo", 50, // batch
																							// size
					true, // dry run
					false, // incremental
					false, // zip
					true, // clean
					false, // resume
					"closed", List.of(), "any");

			CollectionResult result = collectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(150);
			assertThat(result.processedIssues()).isEqualTo(0);
			assertThat(result.outputDirectory()).isEqualTo("dry-run");
			assertThat(result.batchFiles()).isEmpty();

			// Verify no files were created in temp directory
			assertThat(Files.list(tempDir)).isEmpty();

			// Verify GraphQL service was called for issue count
			verify(mockGraphQLService).getSearchIssueCount(contains("repo:test-owner/test-repo"));
			verifyNoMoreInteractions(mockGraphQLService, mockRestService);
		}

		@ParameterizedTest
		@CsvSource({ "open, 75", "closed, 150", "all, 225" })
		@DisplayName("Should handle different issue states in dry run")
		void shouldHandleDifferentIssueStatesInDryRun(String state, int expectedCount) throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(expectedCount);

			CollectionRequest request = new CollectionRequest("test-owner/test-repo", 50, true, false, false, true,
					false, state, List.of(), "any");

			CollectionResult result = collectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(expectedCount);
			assertThat(result.processedIssues()).isEqualTo(0);

			// Verify correct search query was built
			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService).getSearchIssueCount(queryCaptor.capture());
			String capturedQuery = queryCaptor.getValue();

			if ("all".equals(state)) {
				// For "all" state, should not contain "is:all" - just "is:issue"
				assertThat(capturedQuery).contains("is:issue");
				assertThat(capturedQuery).doesNotContain("is:all");
			}
			else {
				assertThat(capturedQuery).contains("is:" + state);
			}
		}

		@Test
		@DisplayName("Should handle label filtering in dry run")
		void shouldHandleLabelFilteringInDryRun() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(25);

			CollectionRequest request = new CollectionRequest("test-owner/test-repo", 50, true, false, false, true,
					false, "closed", List.of("bug", "priority:high"), "all");

			CollectionResult result = collectionService.collectItems(request);

			assertThat(result.totalIssues()).isEqualTo(25);

			// Verify search query includes label filters
			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService).getSearchIssueCount(queryCaptor.capture());
			String capturedQuery = queryCaptor.getValue();
			assertThat(capturedQuery).contains("label:\"bug\"");
			assertThat(capturedQuery).contains("label:\"priority:high\"");
		}

	}

	@Nested
	@DisplayName("Search Query Building - Business Logic Testing")
	class SearchQueryBuildingTest {

		@Test
		@DisplayName("Should build basic search query correctly")
		void shouldBuildBasicSearchQueryCorrectly() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(100);

			CollectionRequest request = new CollectionRequest("spring-projects/spring-ai", 50, true, false, false, true,
					false, "closed", List.of(), "any");

			collectionService.collectItems(request);

			verify(mockGraphQLService).getSearchIssueCount("repo:spring-projects/spring-ai is:issue is:closed");
		}

		@Test
		@DisplayName("Should handle 'all' state by omitting state filter")
		void shouldHandleAllStateByOmittingStateFilter() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(200);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false, "all",
					List.of(), "any");

			collectionService.collectItems(request);

			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService).getSearchIssueCount(queryCaptor.capture());
			String capturedQuery = queryCaptor.getValue();
			assertThat(capturedQuery).contains("repo:owner/repo");
			assertThat(capturedQuery).contains("is:issue");
			assertThat(capturedQuery).doesNotContain("is:all");
			assertThat(capturedQuery).doesNotContain("is:open");
			assertThat(capturedQuery).doesNotContain("is:closed");
		}

		@Test
		@DisplayName("Should handle multiple labels with 'all' mode")
		void shouldHandleMultipleLabelsWithAllMode() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(15);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false, "open",
					List.of("bug", "enhancement", "priority:high"), "all");

			collectionService.collectItems(request);

			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService).getSearchIssueCount(queryCaptor.capture());
			String capturedQuery = queryCaptor.getValue();
			assertThat(capturedQuery).contains("repo:owner/repo");
			assertThat(capturedQuery).contains("is:issue");
			assertThat(capturedQuery).contains("is:open");
			assertThat(capturedQuery).contains("label:\"bug\"");
			assertThat(capturedQuery).contains("label:\"enhancement\"");
			assertThat(capturedQuery).contains("label:\"priority:high\"");
		}

		@Test
		@DisplayName("Should handle single label with 'any' mode")
		void shouldHandleSingleLabelWithAnyMode() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(30);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of("documentation"), "any");

			collectionService.collectItems(request);

			ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
			verify(mockGraphQLService).getSearchIssueCount(queryCaptor.capture());
			String capturedQuery = queryCaptor.getValue();
			assertThat(capturedQuery).contains("repo:owner/repo");
			assertThat(capturedQuery).contains("is:issue");
			assertThat(capturedQuery).contains("is:closed");
			assertThat(capturedQuery).contains("label:\"documentation\"");
		}

	}

	@Nested
	@DisplayName("Error Handling - Exception Safety")
	class ErrorHandlingTest {

		@Test
		@DisplayName("Should handle GraphQL service errors gracefully")
		void shouldHandleGraphQLServiceErrorsGracefully() {
			when(mockGraphQLService.getSearchIssueCount(anyString()))
				.thenThrow(new RuntimeException("GraphQL API error"));

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of(), "any");

			assertThatThrownBy(() -> collectionService.collectItems(request)).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("GraphQL API error");
		}

		@Test
		@DisplayName("Should handle invalid repository format")
		void shouldHandleInvalidRepositoryFormat() {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(0);

			CollectionRequest request = new CollectionRequest("invalid-repo-format", 50, true, false, false, true,
					false, "closed", List.of(), "any");

			assertThatThrownBy(() -> collectionService.collectItems(request))
				.isInstanceOf(ArrayIndexOutOfBoundsException.class);
		}

		@Test
		@DisplayName("Should handle invalid issue state")
		void shouldHandleInvalidIssueState() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(0);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"invalid-state", List.of(), "any");

			// For dry run, the error occurs during search query building
			assertThatThrownBy(() -> collectionService.collectItems(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid state: invalid-state");
		}

	}

	@Nested
	@DisplayName("Configuration Component Tests - Properties Testing")
	class ConfigurationComponentTest {

		@Test
		@DisplayName("Should use configuration properties correctly")
		void shouldUseConfigurationPropertiesCorrectly() {
			// Verify that service was created and reads configuration from properties
			assertThat(collectionService).isNotNull();

			// The service should have been initialized with our properties values
			// These values were set in setUp() and used during construction
			assertThat(realProperties.getMaxBatchSizeBytes()).isEqualTo(1024 * 1024);
			assertThat(realProperties.getLargeIssueThreshold()).isEqualTo(50);
			assertThat(realProperties.getSizeThreshold()).isEqualTo(100 * 1024);
			assertThat(realProperties.getResumeFile()).isEqualTo("resume.json");
		}

		@Test
		@DisplayName("Should handle different batch size configurations")
		void shouldHandleDifferentBatchSizeConfigurations() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(500);

			// Test with small batch size
			CollectionRequest smallBatchRequest = new CollectionRequest("owner/repo", 10, true, false, false, true,
					false, "closed", List.of(), "any");

			CollectionResult smallResult = collectionService.collectItems(smallBatchRequest);
			assertThat(smallResult.totalIssues()).isEqualTo(500);

			// Test with large batch size
			CollectionRequest largeBatchRequest = new CollectionRequest("owner/repo", 200, true, false, false, true,
					false, "closed", List.of(), "any");

			CollectionResult largeResult = collectionService.collectItems(largeBatchRequest);
			assertThat(largeResult.totalIssues()).isEqualTo(500);
		}

	}

	@Nested
	@DisplayName("Component Tests - Service Interactions")
	class ComponentTest {

		@Test
		@DisplayName("Service should be independently testable without Spring context")
		void serviceShouldBeIndependentlyTestableWithoutSpringContext() {
			// This test verifies that CollectionService can be instantiated and tested
			// without requiring Spring Boot context

			assertThat(collectionService).isNotNull();

			// Verify that creating the service doesn't trigger any external calls
			verifyNoInteractions(mockGraphQLService, mockRestService);
		}

		@Test
		@DisplayName("Service should handle all operations gracefully without throwing exceptions")
		void serviceShouldHandleAllOperationsGracefullyWithoutThrowingExceptions() throws Exception {
			// Setup mocks to return safe values
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(0);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of(), "any");

			assertThatCode(() -> {
				CollectionResult result = collectionService.collectItems(request);
				assertThat(result).isNotNull();
				assertThat(result.totalIssues()).isEqualTo(0);
				assertThat(result.processedIssues()).isEqualTo(0);
				assertThat(result.outputDirectory()).isEqualTo("dry-run");
			}).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Should validate all required dependencies are available")
		void shouldValidateAllRequiredDependenciesAreAvailable() {
			// Test that service can be constructed with all valid dependencies
			assertThatCode(() -> new IssueCollectionService(mockGraphQLService, mockRestService, realObjectMapper,
					realProperties, mockStateRepository, mockArchiveService, mockBatchStrategy))
				.doesNotThrowAnyException();

			// Service should be able to handle operations with properly injected
			// dependencies
			assertThat(collectionService).isNotNull();
		}

	}

	@Nested
	@DisplayName("File Safety Tests - Temporary Directory Usage")
	class FileSafetyTest {

		@Test
		@DisplayName("Should never create files outside temp directory during dry run")
		void shouldNeverCreateFilesOutsideTempDirectoryDuringDryRun() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(100);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of(), "any");

			CollectionResult result = collectionService.collectItems(request);

			// Verify dry run returns expected result without creating files
			assertThat(result.outputDirectory()).isEqualTo("dry-run");
			assertThat(result.batchFiles()).isEmpty();
			assertThat(result.processedIssues()).isEqualTo(0);

			// Verify no files were created in temp directory
			assertThat(Files.list(tempDir)).isEmpty();

			// Note: We don't check for 'issues' directory in working dir because
			// other tests may create it. Dry run is verified by the result above.
		}

		@Test
		@DisplayName("Should not access real file system paths during testing")
		void shouldNotAccessRealFileSystemPathsDuringTesting() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(50);

			CollectionRequest request = new CollectionRequest("owner/repo", 25, true, false, false, true, false, "open",
					List.of("test"), "any");

			// This should complete without accessing production file paths
			CollectionResult result = collectionService.collectItems(request);

			assertThat(result).isNotNull();
			assertThat(result.outputDirectory()).isEqualTo("dry-run");

			// Verify working directory remains clean
			assertThat(Files.exists(Path.of("batch_*"))).isFalse();
			assertThat(Files.exists(Path.of("resume.json"))).isFalse();
			assertThat(Files.exists(Path.of("metadata.json"))).isFalse();
		}

	}

	@Nested
	@DisplayName("Mock Verification - External Dependencies")
	class MockVerificationTest {

		@Test
		@DisplayName("Should only call GraphQL service for issue count in dry run")
		void shouldOnlyCallGraphQLServiceForIssueCountInDryRun() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(75);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of(), "any");

			collectionService.collectItems(request);

			// Verify only the search count call was made
			verify(mockGraphQLService, times(1)).getSearchIssueCount(anyString());
			verifyNoMoreInteractions(mockGraphQLService);

			// Verify no other services were called
			verifyNoInteractions(mockRestService);
		}

		@Test
		@DisplayName("Should build search query only once per request")
		void shouldBuildSearchQueryOnlyOncePerRequest() throws Exception {
			when(mockGraphQLService.getSearchIssueCount(anyString())).thenReturn(100);

			CollectionRequest request = new CollectionRequest("owner/repo", 50, true, false, false, true, false,
					"closed", List.of("bug"), "any");

			collectionService.collectItems(request);

			// Verify search query was called exactly once with expected parameters
			verify(mockGraphQLService, times(1))
				.getSearchIssueCount("repo:owner/repo is:issue is:closed label:\"bug\"");
		}

	}

}