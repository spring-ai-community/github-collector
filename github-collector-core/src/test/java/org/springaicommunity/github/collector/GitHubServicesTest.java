package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

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
	private GitHubHttpClient mockHttpClient;

	@Mock
	private GitHubHttpClient mockGraphQLHttpClient;

	private ObjectMapper realObjectMapper;

	@BeforeEach
	void setUp() {
		realObjectMapper = ObjectMapperFactory.create();
	}

	@Nested
	@DisplayName("GitHubRestService Tests")
	class GitHubRestServiceTest {

		private GitHubRestService gitHubRestService;

		@BeforeEach
		void setUp() {
			gitHubRestService = new GitHubRestService(mockHttpClient, realObjectMapper);
		}

		@Test
		@DisplayName("Should get rate limit from GitHub API")
		void shouldGetRateLimitFromGitHubAPI() throws IOException {
			String mockResponse = """
					{
					    "resources": {
					        "core": {
					            "limit": 5000,
					            "remaining": 4999,
					            "reset": 1700000000,
					            "used": 1
					        }
					    }
					}
					""";
			when(mockHttpClient.get("/rate_limit")).thenReturn(mockResponse);

			RateLimitInfo result = gitHubRestService.getRateLimit();

			assertThat(result.limit()).isEqualTo(5000);
			assertThat(result.remaining()).isEqualTo(4999);
			assertThat(result.used()).isEqualTo(1);
			verify(mockHttpClient).get("/rate_limit");
		}

		@Test
		@DisplayName("Should get repository from GitHub API")
		void shouldGetRepositoryFromGitHubAPI() throws IOException {
			String mockResponse = """
					{
					    "id": 12345,
					    "name": "spring-ai",
					    "full_name": "spring-projects/spring-ai",
					    "description": "AI framework for Spring",
					    "html_url": "https://github.com/spring-projects/spring-ai",
					    "private": false,
					    "default_branch": "main"
					}
					""";
			when(mockHttpClient.get("/repos/spring-projects/spring-ai")).thenReturn(mockResponse);

			RepositoryInfo result = gitHubRestService.getRepository("spring-projects/spring-ai");

			assertThat(result.fullName()).isEqualTo("spring-projects/spring-ai");
			assertThat(result.name()).isEqualTo("spring-ai");
			verify(mockHttpClient).get("/repos/spring-projects/spring-ai");
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
				String mockResponse = """
						{
						    "number": 123,
						    "title": "Fix bug",
						    "state": "open",
						    "body": "This fixes the bug",
						    "created_at": "2024-01-15T10:30:00Z",
						    "updated_at": "2024-01-16T11:00:00Z",
						    "url": "https://api.github.com/repos/owner/repo/pulls/123",
						    "html_url": "https://github.com/owner/repo/pull/123",
						    "user": {"login": "author"},
						    "labels": [],
						    "draft": false,
						    "merged": false,
						    "head": {"ref": "feature"},
						    "base": {"ref": "main"},
						    "additions": 10,
						    "deletions": 5,
						    "changed_files": 2
						}
						""";
				when(mockHttpClient.get("/repos/owner/repo/pulls/123")).thenReturn(mockResponse);

				PullRequest result = gitHubRestService.getPullRequest("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result.number()).isEqualTo(123);
				assertThat(result.title()).isEqualTo("Fix bug");
				assertThat(result.state()).isEqualTo("OPEN");
			}

			@Test
			@DisplayName("Should handle get pull request error gracefully")
			void shouldHandleGetPullRequestErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("PR not found"));

				assertThatThrownBy(() -> gitHubRestService.getPullRequest("owner", "repo", 999))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("Failed to get PR #999");
			}

			@Test
			@DisplayName("Should get pull request reviews")
			void shouldGetPullRequestReviews() {
				String mockResponse = """
						[
						    {
						        "id": 1,
						        "body": "LGTM",
						        "state": "APPROVED",
						        "submitted_at": "2024-01-15T12:00:00Z",
						        "user": {"login": "reviewer"},
						        "author_association": "MEMBER",
						        "html_url": "https://github.com/owner/repo/pull/123#pullrequestreview-1"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/pulls/123/reviews")).thenReturn(mockResponse);

				List<Review> result = gitHubRestService.getPullRequestReviews("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result).hasSize(1);
				assertThat(result.get(0).state()).isEqualTo("APPROVED");
				assertThat(result.get(0).author().login()).isEqualTo("reviewer");
			}

			@Test
			@DisplayName("Should handle get reviews error gracefully")
			void shouldHandleGetReviewsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				List<Review> result = gitHubRestService.getPullRequestReviews("owner", "repo", 123);

				assertThat(result).isNotNull();
				assertThat(result).isEmpty();
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
				String mockResponse = """
						{
						    "total_count": 100,
						    "items": [
						        {
						            "number": 1,
						            "title": "PR 1",
						            "state": "open",
						            "created_at": "2024-01-15T10:00:00Z",
						            "updated_at": "2024-01-16T10:00:00Z",
						            "url": "https://api.github.com/repos/owner/repo/pulls/1",
						            "html_url": "https://github.com/owner/repo/pull/1",
						            "user": {"login": "author1"},
						            "labels": [],
						            "draft": false
						        },
						        {
						            "number": 2,
						            "title": "PR 2",
						            "state": "open",
						            "created_at": "2024-01-15T11:00:00Z",
						            "updated_at": "2024-01-16T11:00:00Z",
						            "url": "https://api.github.com/repos/owner/repo/pulls/2",
						            "html_url": "https://github.com/owner/repo/pull/2",
						            "user": {"login": "author2"},
						            "labels": [],
						            "draft": false
						        }
						    ]
						}
						""";
				when(mockHttpClient.get(contains("/search/issues"))).thenReturn(mockResponse);

				SearchResult<PullRequest> result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, null);

				assertThat(result).isNotNull();
				assertThat(result.items()).hasSize(2);
				assertThat(result.items().get(0).number()).isEqualTo(1);
				assertThat(result.items().get(1).number()).isEqualTo(2);
			}

			@Test
			@DisplayName("Should search PRs with cursor/page number")
			void shouldSearchPRsWithCursor() {
				String mockResponse = """
						{
						    "total_count": 100,
						    "items": [{"number": 31, "title": "PR", "state": "open", "created_at": "2024-01-15T10:00:00Z", "updated_at": "2024-01-16T10:00:00Z", "url": "", "html_url": "", "user": {"login": "author"}, "labels": [], "draft": false}]
						}
						""";
				when(mockHttpClient.get(contains("page=2"))).thenReturn(mockResponse);

				SearchResult<PullRequest> result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, "2");

				assertThat(result).isNotNull();
				verify(mockHttpClient).get(contains("page=2"));
			}

			@Test
			@DisplayName("Should handle invalid cursor gracefully")
			void shouldHandleInvalidCursorGracefully() {
				String mockResponse = "{\"total_count\":10,\"items\":[]}";
				when(mockHttpClient.get(anyString())).thenReturn(mockResponse);

				// Invalid cursor should default to page 1
				SearchResult<PullRequest> result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, "invalid");

				assertThat(result).isNotNull();
				verify(mockHttpClient).get(contains("page=1"));
			}

			@Test
			@DisplayName("Should handle search PRs error gracefully")
			void shouldHandleSearchPRsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("Search failed"));

				SearchResult<PullRequest> result = gitHubRestService.searchPRs("repo:owner/repo is:pr", 30, null);

				assertThat(result).isNotNull();
				assertThat(result.items()).isEmpty();
				assertThat(result.hasMore()).isFalse();
			}

		}

		@Nested
		@DisplayName("PR Search Query Building Tests")
		class PRSearchQueryBuildingTest {

			@Test
			@DisplayName("Should build basic PR search query")
			void shouldBuildBasicPRSearchQuery() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", null, "any", null, null);

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("is:open")
					.doesNotContain("label:");
			}

			@Test
			@DisplayName("Should build PR search query for closed state")
			void shouldBuildPRSearchQueryForClosedState() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "closed", null, "any", null, null);

				assertThat(query).contains("repo:owner/repo").contains("is:pr").contains("is:closed");
			}

			@Test
			@DisplayName("Should build PR search query for merged state")
			void shouldBuildPRSearchQueryForMergedState() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "merged", null, "any", null, null);

				assertThat(query).contains("repo:owner/repo").contains("is:pr").contains("is:merged");
			}

			@Test
			@DisplayName("Should build PR search query for all states")
			void shouldBuildPRSearchQueryForAllStates() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "all", null, "any", null, null);

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
						List.of("bug", "priority:high"), "all", null, null);

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("label:\"bug\"")
					.contains("label:\"priority:high\"");
			}

			@Test
			@DisplayName("Should build PR search query with labels in any mode - uses first label")
			void shouldBuildPRSearchQueryWithLabelsInAnyMode() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", List.of("bug", "enhancement"),
						"any", null, null);

				assertThat(query).contains("repo:owner/repo")
					.contains("is:pr")
					.contains("label:\"bug\"")
					.doesNotContain("label:\"enhancement\"");
			}

			@Test
			@DisplayName("Should handle empty labels list")
			void shouldHandleEmptyLabelsList() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "open", List.of(), "any", null, null);

				assertThat(query).contains("repo:owner/repo").contains("is:pr").doesNotContain("label:");
			}

			@Test
			@DisplayName("Should handle null labels")
			void shouldHandleNullLabels() {
				String query = gitHubRestService.buildPRSearchQuery("owner/repo", "closed", null, "all", null, null);

				assertThat(query).contains("repo:owner/repo").contains("is:pr").doesNotContain("label:");
			}

		}

		@Nested
		@DisplayName("Issue Events API Tests")
		class IssueEventsAPITest {

			@Test
			@DisplayName("Should get issue events with label events")
			void shouldGetIssueEventsWithLabelEvents() {
				String mockResponse = """
						[
						    {
						        "id": 12345,
						        "event": "labeled",
						        "actor": {"login": "maintainer"},
						        "label": {"name": "bug", "color": "d73a49", "description": "Something isn't working"},
						        "created_at": "2024-01-15T10:30:00Z"
						    },
						    {
						        "id": 12346,
						        "event": "closed",
						        "actor": {"login": "author"},
						        "created_at": "2024-01-15T11:00:00Z"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/issues/123/events")).thenReturn(mockResponse);

				List<IssueEvent> result = gitHubRestService.getIssueEvents("owner", "repo", 123);

				assertThat(result).hasSize(2);
				assertThat(result.get(0).event()).isEqualTo("labeled");
				assertThat(result.get(0).actor().login()).isEqualTo("maintainer");
				assertThat(result.get(0).label()).isNotNull();
				assertThat(result.get(0).label().name()).isEqualTo("bug");
				assertThat(result.get(1).event()).isEqualTo("closed");
				assertThat(result.get(1).label()).isNull();
			}

			@Test
			@DisplayName("Should handle unlabeled events")
			void shouldHandleUnlabeledEvents() {
				String mockResponse = """
						[
						    {
						        "id": 12347,
						        "event": "unlabeled",
						        "actor": {"login": "reviewer"},
						        "label": {"name": "enhancement", "color": "a2eeef"},
						        "created_at": "2024-01-15T12:00:00Z"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/issues/456/events")).thenReturn(mockResponse);

				List<IssueEvent> result = gitHubRestService.getIssueEvents("owner", "repo", 456);

				assertThat(result).hasSize(1);
				assertThat(result.get(0).event()).isEqualTo("unlabeled");
				assertThat(result.get(0).label()).isNotNull();
				assertThat(result.get(0).label().name()).isEqualTo("enhancement");
			}

			@Test
			@DisplayName("Should handle issue events error gracefully")
			void shouldHandleIssueEventsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				List<IssueEvent> result = gitHubRestService.getIssueEvents("owner", "repo", 123);

				assertThat(result).isEmpty();
			}

			@Test
			@DisplayName("Should handle empty events list")
			void shouldHandleEmptyEventsList() {
				when(mockHttpClient.get("/repos/owner/repo/issues/789/events")).thenReturn("[]");

				List<IssueEvent> result = gitHubRestService.getIssueEvents("owner", "repo", 789);

				assertThat(result).isEmpty();
			}

		}

		@Nested
		@DisplayName("Collaborators API Tests")
		class CollaboratorsAPITest {

			@Test
			@DisplayName("Should get repository collaborators with permissions")
			void shouldGetRepositoryCollaboratorsWithPermissions() {
				String mockResponse = """
						[
						    {
						        "login": "maintainer1",
						        "id": 12345,
						        "type": "User",
						        "permissions": {
						            "admin": true,
						            "maintain": true,
						            "push": true,
						            "triage": true,
						            "pull": true
						        },
						        "role_name": "admin"
						    },
						    {
						        "login": "contributor1",
						        "id": 67890,
						        "type": "User",
						        "permissions": {
						            "admin": false,
						            "maintain": false,
						            "push": true,
						            "triage": true,
						            "pull": true
						        },
						        "role_name": "write"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/collaborators?per_page=100")).thenReturn(mockResponse);

				List<Collaborator> result = gitHubRestService.getRepositoryCollaborators("owner", "repo");

				assertThat(result).hasSize(2);
				assertThat(result.get(0).login()).isEqualTo("maintainer1");
				assertThat(result.get(0).permissions()).isNotNull();
				assertThat(result.get(0).permissions().admin()).isTrue();
				assertThat(result.get(0).roleName()).isEqualTo("admin");
				assertThat(result.get(1).login()).isEqualTo("contributor1");
				assertThat(result.get(1).permissions().admin()).isFalse();
				assertThat(result.get(1).permissions().push()).isTrue();
				assertThat(result.get(1).roleName()).isEqualTo("write");
			}

			@Test
			@DisplayName("Should handle collaborators error gracefully")
			void shouldHandleCollaboratorsErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				List<Collaborator> result = gitHubRestService.getRepositoryCollaborators("owner", "repo");

				assertThat(result).isEmpty();
			}

			@Test
			@DisplayName("Should handle empty collaborators list")
			void shouldHandleEmptyCollaboratorsList() {
				when(mockHttpClient.get("/repos/owner/repo/collaborators?per_page=100")).thenReturn("[]");

				List<Collaborator> result = gitHubRestService.getRepositoryCollaborators("owner", "repo");

				assertThat(result).isEmpty();
			}

			@Test
			@DisplayName("Should handle collaborator without permissions field")
			void shouldHandleCollaboratorWithoutPermissionsField() {
				String mockResponse = """
						[
						    {
						        "login": "user1",
						        "id": 11111,
						        "type": "User"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/collaborators?per_page=100")).thenReturn(mockResponse);

				List<Collaborator> result = gitHubRestService.getRepositoryCollaborators("owner", "repo");

				assertThat(result).hasSize(1);
				assertThat(result.get(0).login()).isEqualTo("user1");
				assertThat(result.get(0).permissions()).isNull();
				assertThat(result.get(0).roleName()).isNull();
			}

		}

		@Nested
		@DisplayName("Releases API Tests")
		class ReleasesAPITest {

			@Test
			@DisplayName("Should get repository releases")
			void shouldGetRepositoryReleases() {
				String mockResponse = """
						[
						    {
						        "id": 12345,
						        "tag_name": "v1.0.0",
						        "name": "Version 1.0.0",
						        "body": "## Bug Fixes\\n- Fixed #123\\n- Fixed #456",
						        "draft": false,
						        "prerelease": false,
						        "created_at": "2024-01-15T10:00:00Z",
						        "published_at": "2024-01-15T12:00:00Z",
						        "author": {"login": "maintainer"},
						        "html_url": "https://github.com/owner/repo/releases/tag/v1.0.0"
						    },
						    {
						        "id": 12346,
						        "tag_name": "v1.0.1",
						        "name": "Version 1.0.1",
						        "body": "## Enhancements\\n- Added feature #789",
						        "draft": false,
						        "prerelease": false,
						        "created_at": "2024-02-01T10:00:00Z",
						        "published_at": "2024-02-01T12:00:00Z",
						        "author": {"login": "maintainer"},
						        "html_url": "https://github.com/owner/repo/releases/tag/v1.0.1"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/releases?per_page=100")).thenReturn(mockResponse);

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).hasSize(2);
				assertThat(result.get(0).tagName()).isEqualTo("v1.0.0");
				assertThat(result.get(0).name()).isEqualTo("Version 1.0.0");
				assertThat(result.get(0).body()).contains("Bug Fixes");
				assertThat(result.get(0).draft()).isFalse();
				assertThat(result.get(0).prerelease()).isFalse();
				assertThat(result.get(0).author().login()).isEqualTo("maintainer");
				assertThat(result.get(1).tagName()).isEqualTo("v1.0.1");
			}

			@Test
			@DisplayName("Should handle draft releases")
			void shouldHandleDraftReleases() {
				String mockResponse = """
						[
						    {
						        "id": 99999,
						        "tag_name": "v2.0.0-draft",
						        "name": "Draft Release",
						        "body": "Work in progress",
						        "draft": true,
						        "prerelease": false,
						        "created_at": "2024-03-01T10:00:00Z",
						        "author": {"login": "developer"},
						        "html_url": "https://github.com/owner/repo/releases/tag/v2.0.0-draft"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/releases?per_page=100")).thenReturn(mockResponse);

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).hasSize(1);
				assertThat(result.get(0).draft()).isTrue();
				assertThat(result.get(0).publishedAt()).isNull();
			}

			@Test
			@DisplayName("Should handle prerelease releases")
			void shouldHandlePrereleaseReleases() {
				String mockResponse = """
						[
						    {
						        "id": 88888,
						        "tag_name": "v1.0.0-M1",
						        "name": "Milestone 1",
						        "body": "First milestone release",
						        "draft": false,
						        "prerelease": true,
						        "created_at": "2024-01-01T10:00:00Z",
						        "published_at": "2024-01-01T12:00:00Z",
						        "author": {"login": "maintainer"},
						        "html_url": "https://github.com/owner/repo/releases/tag/v1.0.0-M1"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/releases?per_page=100")).thenReturn(mockResponse);

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).hasSize(1);
				assertThat(result.get(0).prerelease()).isTrue();
				assertThat(result.get(0).tagName()).isEqualTo("v1.0.0-M1");
			}

			@Test
			@DisplayName("Should handle releases error gracefully")
			void shouldHandleReleasesErrorGracefully() {
				when(mockHttpClient.get(anyString())).thenThrow(new RuntimeException("API error"));

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).isEmpty();
			}

			@Test
			@DisplayName("Should handle empty releases list")
			void shouldHandleEmptyReleasesList() {
				when(mockHttpClient.get("/repos/owner/repo/releases?per_page=100")).thenReturn("[]");

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).isEmpty();
			}

			@Test
			@DisplayName("Should handle release with null optional fields")
			void shouldHandleReleaseWithNullOptionalFields() {
				String mockResponse = """
						[
						    {
						        "id": 77777,
						        "tag_name": "v0.1.0",
						        "draft": false,
						        "prerelease": false,
						        "created_at": "2024-01-01T10:00:00Z",
						        "author": {"login": "dev"},
						        "html_url": "https://github.com/owner/repo/releases/tag/v0.1.0"
						    }
						]
						""";
				when(mockHttpClient.get("/repos/owner/repo/releases?per_page=100")).thenReturn(mockResponse);

				List<Release> result = gitHubRestService.getRepositoryReleases("owner", "repo");

				assertThat(result).hasSize(1);
				assertThat(result.get(0).tagName()).isEqualTo("v0.1.0");
				assertThat(result.get(0).name()).isNull();
				assertThat(result.get(0).body()).isNull();
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
	@DisplayName("GraphQL Rate Limit Error Detection Tests")
	class GraphQLRateLimitTest {

		private GitHubGraphQLService gitHubGraphQLService;

		@BeforeEach
		void setUp() {
			gitHubGraphQLService = new GitHubGraphQLService(mockGraphQLHttpClient, realObjectMapper);
		}

		@Test
		@DisplayName("Should throw on RATE_LIMITED type in GraphQL errors")
		void shouldThrowOnRateLimitedType() {
			String response = """
					{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded"}],"data":null}""";
			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(response);

			assertThatThrownBy(
					() -> gitHubGraphQLService.getSearchIssueCount("repo:spring-projects/spring-ai is:issue"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class)
				.hasMessageContaining("GraphQL rate limit exceeded");
		}

		@Test
		@DisplayName("Should throw on rate limit message in GraphQL errors")
		void shouldThrowOnRateLimitMessage() {
			String response = """
					{"errors":[{"type":"OTHER","message":"You have exceeded a secondary rate limit"}],"data":null}""";
			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(response);

			assertThatThrownBy(
					() -> gitHubGraphQLService.getSearchIssueCount("repo:spring-projects/spring-ai is:issue"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class)
				.hasMessageContaining("rate limit");
		}

		@Test
		@DisplayName("Should NOT throw on non-rate-limit GraphQL errors")
		void shouldNotThrowOnNonRateLimitErrors() {
			// GraphQL errors that aren't rate-limit related should not throw
			String response = """
					{"errors":[{"type":"NOT_FOUND","message":"Could not resolve to a Repository"}],"data":null}""";
			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(response);

			// Should return 0 (default) instead of throwing
			int count = gitHubGraphQLService.getSearchIssueCount("repo:nonexistent/repo is:issue");
			assertThat(count).isEqualTo(0);
		}

		@Test
		@DisplayName("Should handle response with both data and no errors")
		void shouldHandleNormalResponse() {
			String response = "{\"data\":{\"search\":{\"issueCount\":42}}}";
			when(mockGraphQLHttpClient.postGraphQL(anyString())).thenReturn(response);

			int count = gitHubGraphQLService.getSearchIssueCount("repo:spring-projects/spring-ai is:issue");
			assertThat(count).isEqualTo(42);
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

			GitHubRestService restService = new GitHubRestService(mockHttpClient, realObjectMapper);
			GitHubGraphQLService graphQLService = new GitHubGraphQLService(mockGraphQLHttpClient, realObjectMapper);

			assertThat(restService).isNotNull();
			assertThat(graphQLService).isNotNull();

			// Verify that creating these services doesn't trigger any external calls
			verifyNoInteractions(mockHttpClient, mockGraphQLHttpClient);
		}

	}

}
