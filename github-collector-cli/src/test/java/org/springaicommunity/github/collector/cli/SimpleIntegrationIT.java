package org.springaicommunity.github.collector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

import org.springaicommunity.github.collector.*;

/**
 * Simple Integration Tests for GitHub API connectivity.
 *
 * These tests verify basic functionality without strict expectations: 1. GitHub API
 * connectivity works 2. Basic issue collection completes without errors 3. Services can
 * handle real API responses
 *
 * No strict assertions about issue counts - just verify things work.
 *
 * Requires GITHUB_TOKEN environment variable to be set.
 */
@DisplayName("Simple Integration Tests")
@org.junit.jupiter.api.condition.EnabledIf("isGitHubTokenAvailable")
class SimpleIntegrationIT {

	private RestService restService;

	private GraphQLService graphQLService;

	private IssueCollectionService collectionService;

	private ObjectMapper objectMapper;

	@TempDir
	Path tempDir;

	static boolean isGitHubTokenAvailable() {
		String token = EnvironmentSupport.get("GITHUB_TOKEN");
		return token != null && !token.isBlank();
	}

	@BeforeEach
	void setUp() throws Exception {
		objectMapper = ObjectMapperFactory.create();

		// Use GitHubCollectorBuilder to create services
		GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().tokenFromEnv().objectMapper(objectMapper);

		// Get individual services for testing
		restService = builder.buildRestService();
		graphQLService = builder.buildGraphQLService();

		// Setup collection service with minimal properties
		CollectionProperties properties = new CollectionProperties();
		properties.setBatchSize(3); // Very small for testing
		properties.setMaxRetries(1);
		properties.setRetryDelay(500);

		collectionService = GitHubCollectorBuilder.create()
			.tokenFromEnv()
			.objectMapper(objectMapper)
			.properties(properties)
			.buildIssueCollector();
	}

	@Test
	@DisplayName("REST API connectivity test")
	void restApiConnectivityTest() {
		// Just verify we can get repository info via getRepository
		assertThatCode(() -> {
			var repo = restService.getRepository("spring-projects/spring-ai");
			assertThat(repo).isNotNull();
			assertThat(repo.name()).isNotEmpty();
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("GraphQL API connectivity test")
	void graphQLApiConnectivityTest() {
		// Just verify we can count issues without strict expectations
		assertThatCode(() -> {
			int count = graphQLService.getTotalIssueCount("spring-projects", "spring-ai", "all");
			assertThat(count).isGreaterThanOrEqualTo(0);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Basic query building test")
	void basicQueryBuildingTest() {
		// Just test query building without actual collection
		assertThatCode(() -> {
			String query = restService.buildSearchQuery("spring-projects", "spring-ai", "closed", List.of(), "any");
			assertThat(query).contains("repo:spring-projects/spring-ai");
			assertThat(query).contains("is:issue");
			assertThat(query).contains("is:closed");
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Zero results handling test")
	void zeroResultsHandlingTest() throws Exception {
		// Use impossible filter to get zero results
		CollectionRequest request = new CollectionRequest("spring-projects/spring-ai", 3, false, false, false, true,
				false, "open", List.of("impossible-nonexistent-label-12345"), "any");

		String originalDir = System.getProperty("user.dir");
		if (originalDir != null) {
			System.setProperty("user.dir", tempDir.toString());
		}

		try {
			CollectionResult result = collectionService.collectItems(request);

			// Should handle zero results gracefully
			assertThat(result).isNotNull();
			assertThat(result.totalIssues()).isEqualTo(0);
			assertThat(result.processedIssues()).isEqualTo(0);

		}
		finally {
			if (originalDir != null) {
				System.setProperty("user.dir", originalDir);
			}
		}
	}

	@Test
	@DisplayName("Issue events REST API connectivity test")
	void issueEventsApiConnectivityTest() {
		// Test that we can fetch issue events via REST API
		assertThatCode(() -> {
			// Use a known issue number from spring-ai that should have some events
			List<IssueEvent> events = restService.getIssueEvents("spring-projects", "spring-ai", 1);
			// Just verify the API call works - events may be empty for old issues
			assertThat(events).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Collaborators REST API connectivity test")
	void collaboratorsApiConnectivityTest() {
		// Test that we can fetch collaborators via REST API
		// Note: This may return empty list if the token doesn't have sufficient
		// permissions
		assertThatCode(() -> {
			List<Collaborator> collaborators = restService.getRepositoryCollaborators("spring-projects", "spring-ai");
			// Just verify the API call works without throwing
			assertThat(collaborators).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("CollaboratorsCollectionService buildable test")
	void collaboratorsCollectionServiceBuildableTest() {
		// Test that CollaboratorsCollectionService can be built via builder
		assertThatCode(() -> {
			CollaboratorsCollectionService collaboratorsService = GitHubCollectorBuilder.create()
				.tokenFromEnv()
				.objectMapper(objectMapper)
				.buildCollaboratorsCollector();
			assertThat(collaboratorsService).isNotNull();
		}).doesNotThrowAnyException();
	}

	// --- Adaptive windowing integration tests ---

	@Test
	@DisplayName("Issue search count with date range returns valid count")
	void issueSearchCountWithDateRangeTest() {
		// Test that we can query issue count with a date range via the Search API
		assertThatCode(() -> {
			String query = GitHubCollectorBuilder.buildIssueSearchQuery("spring-projects/spring-ai", "closed",
					List.of(), "any", "2024-01-01", "2024-06-01");
			int count = graphQLService.getSearchIssueCount(query);
			// Just verify the API call works and returns a non-negative count
			assertThat(count).isGreaterThanOrEqualTo(0);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("PR search count with date range returns valid count")
	void prSearchCountWithDateRangeTest() {
		// Test that we can query PR count with a date range via the Search API
		assertThatCode(() -> {
			String query = restService.buildPRSearchQuery("spring-projects/spring-ai", "closed", List.of(), "any",
					"2024-01-01", "2024-06-01");
			assertThat(query).contains("created:2024-01-01..2024-06-01");
			int count = restService.getTotalPRCount(query);
			assertThat(count).isGreaterThanOrEqualTo(0);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Windowed issue collector can be built via builder")
	void windowedIssueCollectorBuildableTest() {
		assertThatCode(() -> {
			CollectionRequest request = CollectionRequest.builder()
				.repository("spring-projects/spring-ai")
				.batchSize(100)
				.issueState("closed")
				.collectionType("issues")
				.createdAfter("2024-01-01")
				.createdBefore("2024-06-01")
				.build();

			WindowedCollectionService<Issue> windowed = GitHubCollectorBuilder.create()
				.tokenFromEnv()
				.objectMapper(objectMapper)
				.buildWindowedIssueCollector(request);
			assertThat(windowed).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Windowed PR collector can be built via builder")
	void windowedPRCollectorBuildableTest() {
		assertThatCode(() -> {
			CollectionRequest request = CollectionRequest.builder()
				.repository("spring-projects/spring-ai")
				.batchSize(100)
				.prState("closed")
				.collectionType("prs")
				.createdAfter("2024-01-01")
				.createdBefore("2024-06-01")
				.build();

			WindowedCollectionService<AnalyzedPullRequest> windowed = GitHubCollectorBuilder.create()
				.tokenFromEnv()
				.objectMapper(objectMapper)
				.buildWindowedPRCollector(request);
			assertThat(windowed).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("PR state is normalized to uppercase")
	void prStateNormalizationTest() {
		// Fetch a real PR and verify state is uppercase
		assertThatCode(() -> {
			PullRequest pr = restService.getPullRequest("spring-projects", "spring-ai", 1);
			assertThat(pr).isNotNull();
			// State should be uppercase (OPEN, CLOSED, or MERGED)
			assertThat(pr.state()).matches("[A-Z]+");
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("AdaptiveWindowPlanner works with real search counts")
	void adaptiveWindowPlannerWithRealCountsTest() {
		// Use a narrow date range that should fit in a single window
		assertThatCode(() -> {
			AdaptiveWindowPlanner planner = new AdaptiveWindowPlanner(900);
			List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows("2024-01-01", "2024-02-01",
					(after, before) -> {
						String query = GitHubCollectorBuilder.buildIssueSearchQuery("spring-projects/spring-ai",
								"closed", List.of(), "any", after, before);
						return graphQLService.getSearchIssueCount(query);
					});

			// A single month of spring-ai closed issues should fit in one window
			assertThat(windows).isNotEmpty();
			assertThat(windows.get(0).createdAfter()).isEqualTo("2024-01-01");
			assertThat(windows.get(windows.size() - 1).createdBefore()).isEqualTo("2024-02-01");
		}).doesNotThrowAnyException();
	}

}
