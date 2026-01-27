package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GitHubServices using plain JUnit with mocked APIs. NO Spring context to
 * prevent accidental production operations. NO real GitHub API calls - all dependencies
 * mocked.
 */
@DisplayName("GitHubServices Tests - Plain JUnit with Mocked APIs")
@ExtendWith(MockitoExtension.class)
class GitHubServicesTest {

	@Mock
	private GitHub mockGitHub;

	@Mock
	private GitHubHttpClient mockHttpClient;

	@Mock
	private GitHubHttpClient mockGraphQLHttpClient;

	private ObjectMapper realObjectMapper;

	@BeforeEach
	void setUp() {
		realObjectMapper = new ObjectMapper();
		realObjectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
	}

	@Nested
	@DisplayName("GitHubRestService Tests")
	class GitHubRestServiceTest {

		private GitHubRestService gitHubRestService;

		@BeforeEach
		void setUp() {
			gitHubRestService = new GitHubRestService(mockGitHub, mockHttpClient, realObjectMapper);
		}

		@Test
		@DisplayName("Should get rate limit from GitHub API")
		void shouldGetRateLimitFromGitHubAPI() throws IOException {
			GHRateLimit mockRateLimit = mock(GHRateLimit.class);
			when(mockGitHub.getRateLimit()).thenReturn(mockRateLimit);

			GHRateLimit result = gitHubRestService.getRateLimit();

			assertThat(result).isEqualTo(mockRateLimit);
			verify(mockGitHub).getRateLimit();
		}

		@Test
		@DisplayName("Should get repository from GitHub API")
		void shouldGetRepositoryFromGitHubAPI() throws IOException {
			GHRepository mockRepository = mock(GHRepository.class);
			when(mockGitHub.getRepository("spring-projects/spring-ai")).thenReturn(mockRepository);

			GHRepository result = gitHubRestService.getRepository("spring-projects/spring-ai");

			assertThat(result).isEqualTo(mockRepository);
			verify(mockGitHub).getRepository("spring-projects/spring-ai");
		}

		@Test
		@DisplayName("Should get repository info via REST API")
		void shouldGetRepositoryInfoViaRestAPI() {
			String mockResponse = "{\"name\":\"spring-ai\",\"full_name\":\"spring-projects/spring-ai\"}";

			when(mockHttpClient.get(anyString())).thenReturn(mockResponse);

			JsonNode result = gitHubRestService.getRepositoryInfo("spring-projects", "spring-ai");

			assertThat(result).isNotNull();
			assertThat(result.path("name").asText()).isEqualTo("spring-ai");
			assertThat(result.path("full_name").asText()).isEqualTo("spring-projects/spring-ai");
		}

		@Test
		@DisplayName("Should handle repository info parsing errors gracefully")
		void shouldHandleRepositoryInfoParsingErrorsGracefully() {
			String invalidResponse = "invalid json";

			when(mockHttpClient.get(anyString())).thenReturn(invalidResponse);

			JsonNode result = gitHubRestService.getRepositoryInfo("spring-projects", "spring-ai");

			assertThat(result).isNotNull();
			assertThat(result.isObject()).isTrue();
			assertThat(result.size()).isEqualTo(0); // Empty object node
		}

		@Test
		@DisplayName("Should get total issue count via search API")
		void shouldGetTotalIssueCountViaSearchAPI() {
			String mockResponse = "{\"total_count\":1500,\"incomplete_results\":false}";

			when(mockHttpClient.get(anyString())).thenReturn(mockResponse);

			int result = gitHubRestService.getTotalIssueCount("spring-projects", "spring-ai", "closed");

			assertThat(result).isEqualTo(1500);
		}

		@Test
		@DisplayName("Should handle issue count API errors gracefully")
		void shouldHandleIssueCountAPIErrorsGracefully() {
			when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API Error"));

			int result = gitHubRestService.getTotalIssueCount("spring-projects", "spring-ai", "closed");

			assertThat(result).isEqualTo(0);
		}

		@Nested
		@DisplayName("Search Query Building Tests")
		class SearchQueryBuildingTest {

			@Test
			@DisplayName("Should build basic search query")
			void shouldBuildBasicSearchQuery() {
				String query = gitHubRestService.buildSearchQuery("spring-projects", "spring-ai", "closed", null,
						"any");

				assertThat(query).contains("repo:spring-projects/spring-ai")
					.contains("is:issue")
					.contains("is:closed")
					.doesNotContain("label:");
			}

			@Test
			@DisplayName("Should build search query for all states")
			void shouldBuildSearchQueryForAllStates() {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", "all", null, "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:issue")
					.doesNotContain("is:all")
					.doesNotContain("is:closed")
					.doesNotContain("is:open");
			}

			@ParameterizedTest
			@ValueSource(strings = { "open", "closed" })
			@DisplayName("Should build search query for specific states")
			void shouldBuildSearchQueryForSpecificStates(String state) {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", state, null, "any");

				assertThat(query).contains("repo:owner/repo").contains("is:issue").contains("is:" + state);
			}

			@Test
			@DisplayName("Should build search query with single label in any mode")
			void shouldBuildSearchQueryWithSingleLabelInAnyMode() {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", "closed", List.of("bug"), "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:issue")
					.contains("is:closed")
					.contains("label:\"bug\"");
			}

			@Test
			@DisplayName("Should build search query with multiple labels in all mode")
			void shouldBuildSearchQueryWithMultipleLabelsInAllMode() {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", "open",
						List.of("bug", "enhancement", "priority:high"), "all");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:issue")
					.contains("is:open")
					.contains("label:\"bug\"")
					.contains("label:\"enhancement\"")
					.contains("label:\"priority:high\"");
			}

			@Test
			@DisplayName("Should build search query with multiple labels in any mode - uses first label only")
			void shouldBuildSearchQueryWithMultipleLabelsInAnyMode() {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", "closed",
						List.of("bug", "enhancement"), "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:issue")
					.contains("is:closed")
					.contains("label:\"bug\"")
					.doesNotContain("label:\"enhancement\"");
			}

			@Test
			@DisplayName("Should handle empty labels list")
			void shouldHandleEmptyLabelsList() {
				String query = gitHubRestService.buildSearchQuery("owner", "repo", "open", List.of(), "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:issue")
					.contains("is:open")
					.doesNotContain("label:");
			}

		}

		@Nested
		@DisplayName("Pull Request API Tests")
		class PullRequestAPITest {

			@Test
			@DisplayName("Should get pull request by number")
			void shouldGetPullRequestByNumber() {
				String mockResponse = "{\"number\":123,\"title\":\"Fix bug\",\"state\":\"open\"}";
				when(mockHttpClient.get("/repos/owner/repo/pulls/123")).thenReturn(mockResponse);

				JsonNode result = gitHubRestService.getPullRequest("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result.path("number").asInt()).isEqualTo(123);
				assertThat(result.path("title").asText()).isEqualTo("Fix bug");
				assertThat(result.path("state").asText()).isEqualTo("open");
			}

			@Test
			@DisplayName("Should handle get pull request error gracefully")
			void shouldHandleGetPullRequestErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("PR not found"));

				JsonNode result = gitHubRestService.getPullRequest("owner", "repo", 999);

				assertThat(result).isNotNull();
				assertThat(result.isObject()).isTrue();
				assertThat(result.size()).isEqualTo(0);
			}

			@Test
			@DisplayName("Should get pull request reviews")
			void shouldGetPullRequestReviews() {
				String mockResponse = "[{\"id\":1,\"state\":\"APPROVED\",\"user\":{\"login\":\"reviewer\"}}]";
				when(mockHttpClient.get("/repos/owner/repo/pulls/123/reviews")).thenReturn(mockResponse);

				JsonNode result = gitHubRestService.getPullRequestReviews("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result.isArray()).isTrue();
				assertThat(result.size()).isEqualTo(1);
				assertThat(result.get(0).path("state").asText()).isEqualTo("APPROVED");
			}

			@Test
			@DisplayName("Should handle get reviews error gracefully")
			void shouldHandleGetReviewsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				JsonNode result = gitHubRestService.getPullRequestReviews("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result.isArray()).isTrue();
				assertThat(result.size()).isEqualTo(0);
			}

			@Test
			@DisplayName("Should get total PR count")
			void shouldGetTotalPRCount() {
				String mockResponse = "{\"total_count\":250,\"incomplete_results\":false}";
				when(mockHttpClient.get(contains("/search/issues"))).thenReturn(mockResponse);

				int result = gitHubRestService.getTotalPRCount("repo:owner/repo is:pr");

				assertThat(result).isEqualTo(250);
			}

			@Test
			@DisplayName("Should handle PR count error gracefully")
			void shouldHandlePRCountErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				int result = gitHubRestService.getTotalPRCount("repo:owner/repo is:pr");

				assertThat(result).isEqualTo(0);
			}

			@Test
			@DisplayName("Should search PRs with pagination")
			void shouldSearchPRsWithPagination() {
				String mockResponse = "{\"total_count\":100,\"items\":[{\"number\":1},{\"number\":2}]}";
				when(mockHttpClient.get(contains("/search/issues"))).thenReturn(mockResponse);

				JsonNode result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, null);

				assertThat(result).isNotNull();
				assertThat(result.path("total_count").asInt()).isEqualTo(100);
				assertThat(result.path("items").size()).isEqualTo(2);
			}

			@Test
			@DisplayName("Should search PRs with cursor/page number")
			void shouldSearchPRsWithCursor() {
				String mockResponse = "{\"total_count\":100,\"items\":[{\"number\":31}]}";
				when(mockHttpClient.get(contains("page=2"))).thenReturn(mockResponse);

				JsonNode result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, "2");

				assertThat(result).isNotNull();
				verify(mockHttpClient).get(contains("page=2"));
			}

			@Test
			@DisplayName("Should handle invalid cursor gracefully")
			void shouldHandleInvalidCursorGracefully() {
				String mockResponse = "{\"total_count\":10,\"items\":[]}";
				when(mockHttpClient.get(anyString())).thenReturn(mockResponse);

				// Invalid cursor should default to page 1
				JsonNode result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, "invalid");

				assertThat(result).isNotNull();
				verify(mockHttpClient).get(contains("page=1"));
			}

			@Test
			@DisplayName("Should handle search PRs error gracefully")
			void shouldHandleSearchPRsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("Search failed"));

				JsonNode result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, null);

				assertThat(result).isNotNull();
				assertThat(result.isObject()).isTrue();
				assertThat(result.size()).isEqualTo(0);
			}

		}

		@Nested
		@DisplayName("PR Search Query Building Tests")
		class PRSearchQueryBuildingTest {

			@Test
			@DisplayName("Should build basic PR search query")
			void shouldBuildBasicPRSearchQuery() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", null, "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("is:open")
					.doesNotContain("label:");
			}

			@Test
			@DisplayName("Should build PR search query for closed state")
			void shouldBuildPRSearchQueryForClosedState() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "closed", null, "any");

				assertThat(query).contains("repo:owner/repo").contains("is:pr").contains("is:closed");
			}

			@Test
			@DisplayName("Should build PR search query for merged state")
			void shouldBuildPRSearchQueryForMergedState() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "merged", null, "any");

				assertThat(query).contains("repo:owner/repo").contains("is:pr").contains("is:merged");
			}

			@Test
			@DisplayName("Should build PR search query for all states")
			void shouldBuildPRSearchQueryForAllStates() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "all", null, "any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.doesNotContain("is:open")
					.doesNotContain("is:closed")
					.doesNotContain("is:merged")
					.doesNotContain("is:all");
			}

			@Test
			@DisplayName("Should build PR search query with labels in all mode")
			void shouldBuildPRSearchQueryWithLabelsInAllMode() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open",
						List.of("bug", "priority:high"), "all");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("label:\"bug\"")
					.contains("label:\"priority:high\"");
			}

			@Test
			@DisplayName("Should build PR search query with labels in any mode - uses first label")
			void shouldBuildPRSearchQueryWithLabelsInAnyMode() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", List.of("bug", "enhancement"),
						"any");

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("label:\"bug\"")
					.doesNotContain("label:\"enhancement\"");
			}

			@Test
			@DisplayName("Should handle empty labels list")
			void shouldHandleEmptyLabelsList() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", List.of(), "any");

				assertThat(query).contains("repo:owner/repo").contains("is:pr").doesNotContain("label:");
			}

			@Test
			@DisplayName("Should handle null labels")
			void shouldHandleNullLabels() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "closed", null, "all");

				assertThat(query).contains("repo:owner/repo").contains("is:pr").doesNotContain("label:");
			}

		}

	}

	@Nested
	@DisplayName("GitHubGraphQLService Tests")
	class GitHubGraphQLServiceTest {

		private GitHubGraphQLService gitHubGraphQLService;

		@BeforeEach
		void setUp() {
			gitHubGraphQLService = new GitHubGraphQLService(mockGraphQLHttpClient, realObjectMapper);
		}

		@Test
		@DisplayName("Should execute GraphQL query successfully")
		void shouldExecuteGraphQLQuerySuccessfully() {
			String mockResponse = "{\"data\":{\"repository\":{\"name\":\"spring-ai\"}}}";
			String testQuery = "query { repository(owner: \"spring-projects\", name: \"spring-ai\") { name } }";
			Object testVariables = null;

			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(mockResponse);

			JsonNode result = gitHubGraphQLService.executeQuery(testQuery, testVariables);

			assertThat(result).isNotNull();
			assertThat(result.path("data").path("repository").path("name").asText()).isEqualTo("spring-ai");
		}

		@Test
		@DisplayName("Should handle GraphQL query errors gracefully")
		void shouldHandleGraphQLQueryErrorsGracefully() {
			String testQuery = "invalid query";

			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenThrow(new RuntimeException("GraphQL Error"));

			JsonNode result = gitHubGraphQLService.executeQuery(testQuery, null);

			assertThat(result).isNotNull();
			assertThat(result.isObject()).isTrue();
			assertThat(result.size()).isEqualTo(0); // Empty object node
		}

		@Test
		@DisplayName("Should get total issue count via GraphQL")
		void shouldGetTotalIssueCountViaGraphQL() {
			String mockResponse = "{\"data\":{\"repository\":{\"issues\":{\"totalCount\":2500}}}}";

			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(mockResponse);

			int result = gitHubGraphQLService.getTotalIssueCount("spring-projects", "spring-ai", "closed");

			assertThat(result).isEqualTo(2500);
		}

		@Test
		@DisplayName("Should get search issue count via GraphQL")
		void shouldGetSearchIssueCountViaGraphQL() {
			String mockResponse = "{\"data\":{\"search\":{\"issueCount\":750}}}";

			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(mockResponse);

			int result = gitHubGraphQLService
				.getSearchIssueCount("repo:spring-projects/spring-ai is:issue is:closed label:\"bug\"");

			assertThat(result).isEqualTo(750);
		}

		@ParameterizedTest
		@CsvSource({ "spring-projects, spring-ai, open, 1000", "microsoft, vscode, closed, 5000",
				"kubernetes, kubernetes, all, 10000" })
		@DisplayName("Should handle different repository and state combinations")
		void shouldHandleDifferentRepositoryAndStateCombinations(String owner, String repo, String state,
				int expectedCount) {
			String mockResponse = String.format("{\"data\":{\"repository\":{\"issues\":{\"totalCount\":%d}}}}",
					expectedCount);

			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(mockResponse);

			int result = gitHubGraphQLService.getTotalIssueCount(owner, repo, state);

			assertThat(result).isEqualTo(expectedCount);
		}

	}

	@Nested
	@DisplayName("JsonNodeUtils Tests")
	class JsonNodeUtilsTest {

		private JsonNodeUtils jsonNodeUtils;

		private JsonNode testNode;

		@BeforeEach
		void setUp() throws Exception {
			jsonNodeUtils = new JsonNodeUtils();

			// Create a test JSON structure
			String testJson = """
					{
					    "user": {
					        "name": "John Doe",
					        "age": 30,
					        "email": "john@example.com",
					        "created_at": "2023-01-15T10:30:00"
					    },
					    "issues": [
					        {"title": "Bug fix", "number": 1},
					        {"title": "Feature request", "number": 2}
					    ],
					    "empty_array": [],
					    "missing_field": null
					}
					""";

			testNode = realObjectMapper.readTree(testJson);
		}

		@Test
		@DisplayName("Should get string value from valid path")
		void shouldGetStringValueFromValidPath() {
			Optional<String> result = jsonNodeUtils.getString(testNode, "user", "name");

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("John Doe");
		}

		@Test
		@DisplayName("Should return empty for missing string path")
		void shouldReturnEmptyForMissingStringPath() {
			Optional<String> result = jsonNodeUtils.getString(testNode, "user", "nonexistent");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should get integer value from valid path")
		void shouldGetIntegerValueFromValidPath() {
			Optional<Integer> result = jsonNodeUtils.getInt(testNode, "user", "age");

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo(30);
		}

		@Test
		@DisplayName("Should return empty for missing integer path")
		void shouldReturnEmptyForMissingIntegerPath() {
			Optional<Integer> result = jsonNodeUtils.getInt(testNode, "user", "nonexistent");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should get datetime value from valid path")
		void shouldGetDatetimeValueFromValidPath() {
			Optional<LocalDateTime> result = jsonNodeUtils.getDateTime(testNode, "user", "created_at");

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 30, 0));
		}

		@Test
		@DisplayName("Should return empty for invalid datetime format")
		void shouldReturnEmptyForInvalidDatetimeFormat() {
			Optional<LocalDateTime> result = jsonNodeUtils.getDateTime(testNode, "user", "name"); // Name
																									// is
																									// not
																									// a
																									// datetime

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should get array values from valid path")
		void shouldGetArrayValuesFromValidPath() {
			List<JsonNode> result = jsonNodeUtils.getArray(testNode, "issues");

			assertThat(result).hasSize(2);
			assertThat(result.get(0).path("title").asText()).isEqualTo("Bug fix");
			assertThat(result.get(1).path("title").asText()).isEqualTo("Feature request");
		}

		@Test
		@DisplayName("Should return empty list for non-array path")
		void shouldReturnEmptyListForNonArrayPath() {
			List<JsonNode> result = jsonNodeUtils.getArray(testNode, "user", "name");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should return empty list for missing array path")
		void shouldReturnEmptyListForMissingArrayPath() {
			List<JsonNode> result = jsonNodeUtils.getArray(testNode, "nonexistent");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should handle empty array correctly")
		void shouldHandleEmptyArrayCorrectly() {
			List<JsonNode> result = jsonNodeUtils.getArray(testNode, "empty_array");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Should handle nested path navigation")
		void shouldHandleNestedPathNavigation() {
			Optional<String> result = jsonNodeUtils.getString(testNode, "user", "email");

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("john@example.com");
		}

		@Test
		@DisplayName("Should handle deep path navigation with missing intermediate nodes")
		void shouldHandleDeepPathNavigationWithMissingIntermediateNodes() {
			Optional<String> result = jsonNodeUtils.getString(testNode, "nonexistent", "deep", "path");

			assertThat(result).isEmpty();
		}

	}

	@Nested
	@DisplayName("Component Tests - Service Construction")
	class ComponentTest {

		@Test
		@DisplayName("Services should be independently testable without Spring context")
		void servicesShouldBeIndependentlyTestableWithoutSpringContext() {
			// This test verifies that all services can be instantiated and tested
			// without requiring Spring Boot context

			GitHubRestService restService = new GitHubRestService(mockGitHub, mockHttpClient, realObjectMapper);
			GitHubGraphQLService graphQLService = new GitHubGraphQLService(mockGraphQLHttpClient, realObjectMapper);
			JsonNodeUtils jsonUtils = new JsonNodeUtils();

			assertThat(restService).isNotNull();
			assertThat(graphQLService).isNotNull();
			assertThat(jsonUtils).isNotNull();

			// Verify that creating these services doesn't trigger any external calls
			verifyNoInteractions(mockGitHub, mockHttpClient, mockGraphQLHttpClient);
		}

		@Test
		@DisplayName("All services should handle errors gracefully without throwing exceptions")
		void allServicesShouldHandleErrorsGracefullyWithoutThrowingExceptions() {
			JsonNodeUtils jsonUtils = new JsonNodeUtils();

			// Test that JsonNodeUtils handles missing fields gracefully
			assertThatCode(() -> {
				jsonUtils.getString(realObjectMapper.createObjectNode(), "nonexistent");
				jsonUtils.getInt(realObjectMapper.createObjectNode(), "nonexistent");
				jsonUtils.getDateTime(realObjectMapper.createObjectNode(), "nonexistent");
				jsonUtils.getArray(realObjectMapper.createObjectNode(), "nonexistent");
			}).doesNotThrowAnyException();
		}

	}

}
