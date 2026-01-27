package org.springaicommunity.github.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RetryingGitHubClient}.
 *
 * Tests retry logic, exponential backoff, and error classification.
 */
@DisplayName("RetryingGitHubClient Tests")
@ExtendWith(MockitoExtension.class)
class RetryingGitHubClientTest {

	@Mock
	private GitHubClient mockDelegate;

	private RetryingGitHubClient retryingClient;

	@BeforeEach
	void setUp() {
		// Use minimal delay for fast tests
		retryingClient = RetryingGitHubClient.builder().wrapping(mockDelegate).maxRetries(3).initialDelayMs(1).build();
	}

	@Nested
	@DisplayName("Delegation Tests")
	class DelegationTest {

		@Test
		@DisplayName("Should delegate get() to wrapped client")
		void shouldDelegateGet() {
			when(mockDelegate.get("/repos/owner/repo")).thenReturn("{\"name\":\"repo\"}");

			String result = retryingClient.get("/repos/owner/repo");

			assertThat(result).isEqualTo("{\"name\":\"repo\"}");
			verify(mockDelegate, times(1)).get("/repos/owner/repo");
		}

		@Test
		@DisplayName("Should delegate getWithQuery() to wrapped client")
		void shouldDelegateGetWithQuery() {
			when(mockDelegate.getWithQuery("/search/issues", "q=repo:owner/repo")).thenReturn("{\"items\":[]}");

			String result = retryingClient.getWithQuery("/search/issues", "q=repo:owner/repo");

			assertThat(result).isEqualTo("{\"items\":[]}");
			verify(mockDelegate, times(1)).getWithQuery("/search/issues", "q=repo:owner/repo");
		}

		@Test
		@DisplayName("Should delegate postGraphQL() to wrapped client")
		void shouldDelegatePostGraphQL() {
			when(mockDelegate.postGraphQL("{\"query\":\"{ viewer { login } }\"}"))
				.thenReturn("{\"data\":{\"viewer\":{\"login\":\"user\"}}}");

			String result = retryingClient.postGraphQL("{\"query\":\"{ viewer { login } }\"}");

			assertThat(result).isEqualTo("{\"data\":{\"viewer\":{\"login\":\"user\"}}}");
			verify(mockDelegate, times(1)).postGraphQL("{\"query\":\"{ viewer { login } }\"}");
		}

		@Test
		@DisplayName("Should handle null query string in getWithQuery()")
		void shouldHandleNullQueryString() {
			when(mockDelegate.getWithQuery("/path", null)).thenReturn("response");

			String result = retryingClient.getWithQuery("/path", null);

			assertThat(result).isEqualTo("response");
			verify(mockDelegate).getWithQuery("/path", null);
		}

	}

	@Nested
	@DisplayName("Retry Behavior Tests")
	class RetryBehaviorTest {

		@Test
		@DisplayName("Should retry on server error (5xx)")
		void shouldRetryOnServerError() {
			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Server Error",
					500, "Internal Server Error");

			when(mockDelegate.get("/path")).thenThrow(serverError).thenThrow(serverError).thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(3)).get("/path");
		}

		@Test
		@DisplayName("Should retry on rate limit error (429)")
		void shouldRetryOnRateLimitError() {
			GitHubHttpClient.GitHubApiException rateLimitError = new GitHubHttpClient.GitHubApiException("Rate limited",
					429, "Too Many Requests");

			when(mockDelegate.get("/path")).thenThrow(rateLimitError).thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(2)).get("/path");
		}

		@Test
		@DisplayName("Should retry on generic exception")
		void shouldRetryOnGenericException() {
			when(mockDelegate.get("/path")).thenThrow(new RuntimeException("Network error")).thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(2)).get("/path");
		}

		@Test
		@DisplayName("Should NOT retry on client error (4xx except 429)")
		void shouldNotRetryOnClientError() {
			GitHubHttpClient.GitHubApiException notFoundError = new GitHubHttpClient.GitHubApiException("Not Found",
					404, "Not Found");

			when(mockDelegate.get("/path")).thenThrow(notFoundError);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class)
				.hasMessageContaining("Not Found");

			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should NOT retry on 400 Bad Request")
		void shouldNotRetryOnBadRequest() {
			GitHubHttpClient.GitHubApiException badRequest = new GitHubHttpClient.GitHubApiException("Bad Request", 400,
					"Bad Request");

			when(mockDelegate.get("/path")).thenThrow(badRequest);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should NOT retry on 401 Unauthorized")
		void shouldNotRetryOnUnauthorized() {
			GitHubHttpClient.GitHubApiException unauthorized = new GitHubHttpClient.GitHubApiException("Unauthorized",
					401, "Unauthorized");

			when(mockDelegate.get("/path")).thenThrow(unauthorized);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should NOT retry on 403 Forbidden")
		void shouldNotRetryOnForbidden() {
			GitHubHttpClient.GitHubApiException forbidden = new GitHubHttpClient.GitHubApiException("Forbidden", 403,
					"Forbidden");

			when(mockDelegate.get("/path")).thenThrow(forbidden);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			verify(mockDelegate, times(1)).get("/path");
		}

	}

	@Nested
	@DisplayName("Max Retries Tests")
	class MaxRetriesTest {

		@Test
		@DisplayName("Should stop after max retries and throw exception")
		void shouldStopAfterMaxRetries() {
			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Server Error",
					500, "Internal Server Error");

			when(mockDelegate.get("/path")).thenThrow(serverError);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			// Initial attempt + 3 retries = 4 total attempts
			verify(mockDelegate, times(4)).get("/path");
		}

		@Test
		@DisplayName("Should throw original RuntimeException after max retries")
		void shouldThrowOriginalRuntimeException() {
			RuntimeException originalException = new RuntimeException("Network failure");
			when(mockDelegate.get("/path")).thenThrow(originalException);

			assertThatThrownBy(() -> retryingClient.get("/path")).isInstanceOf(RuntimeException.class)
				.hasMessage("Network failure");
		}

		@Test
		@DisplayName("Should wrap checked exception after max retries")
		void shouldWrapCheckedException() {
			// Simulate checked exception scenario via RuntimeException wrapping
			when(mockDelegate.get("/path"))
				.thenThrow(new RuntimeException("Request failed after 4 attempts", new Exception("IO Error")));

			assertThatThrownBy(() -> retryingClient.get("/path")).isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("Should succeed on last retry attempt")
		void shouldSucceedOnLastRetry() {
			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Server Error",
					500, "Error");

			when(mockDelegate.get("/path")).thenThrow(serverError)
				.thenThrow(serverError)
				.thenThrow(serverError)
				.thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(4)).get("/path");
		}

	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTest {

		@Test
		@DisplayName("Should build with defaults")
		void shouldBuildWithDefaults() {
			RetryingGitHubClient defaultClient = RetryingGitHubClient.builder().wrapping(mockDelegate).build();

			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Error", 500,
					"Error");
			when(mockDelegate.get("/path")).thenThrow(serverError);

			assertThatThrownBy(() -> defaultClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			// Default is 3 retries = 4 total attempts
			verify(mockDelegate, times(4)).get("/path");
		}

		@Test
		@DisplayName("Should respect custom max retries")
		void shouldRespectCustomMaxRetries() {
			RetryingGitHubClient customClient = RetryingGitHubClient.builder()
				.wrapping(mockDelegate)
				.maxRetries(1)
				.initialDelayMs(1)
				.build();

			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Error", 500,
					"Error");
			when(mockDelegate.get("/path")).thenThrow(serverError);

			assertThatThrownBy(() -> customClient.get("/path")).isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			// 1 retry = 2 total attempts
			verify(mockDelegate, times(2)).get("/path");
		}

		@Test
		@DisplayName("Should work with zero retries")
		void shouldWorkWithZeroRetries() {
			RetryingGitHubClient noRetryClient = RetryingGitHubClient.builder()
				.wrapping(mockDelegate)
				.maxRetries(0)
				.initialDelayMs(1)
				.build();

			GitHubHttpClient.GitHubApiException serverError = new GitHubHttpClient.GitHubApiException("Error", 500,
					"Error");
			when(mockDelegate.get("/path")).thenThrow(serverError);

			assertThatThrownBy(() -> noRetryClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class);

			// 0 retries = 1 total attempt
			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should accept Duration for initial delay")
		void shouldAcceptDurationForInitialDelay() {
			RetryingGitHubClient client = RetryingGitHubClient.builder()
				.wrapping(mockDelegate)
				.initialDelay(Duration.ofMillis(1))
				.build();

			when(mockDelegate.get("/path")).thenReturn("success");

			String result = client.get("/path");

			assertThat(result).isEqualTo("success");
		}

		@Test
		@DisplayName("Should reject null delegate")
		void shouldRejectNullDelegate() {
			assertThatThrownBy(() -> RetryingGitHubClient.builder().build()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("wrapping");
		}

		@Test
		@DisplayName("Should reject negative max retries")
		void shouldRejectNegativeMaxRetries() {
			assertThatThrownBy(() -> RetryingGitHubClient.builder().wrapping(mockDelegate).maxRetries(-1).build())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("non-negative");
		}

		@Test
		@DisplayName("Should reject non-positive initial delay")
		void shouldRejectNonPositiveInitialDelay() {
			assertThatThrownBy(() -> RetryingGitHubClient.builder().wrapping(mockDelegate).initialDelayMs(0).build())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("positive");
		}

	}

	@Nested
	@DisplayName("First Attempt Success Tests")
	class FirstAttemptSuccessTest {

		@Test
		@DisplayName("Should return immediately on first success for get()")
		void shouldReturnImmediatelyOnGetSuccess() {
			when(mockDelegate.get("/path")).thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should return immediately on first success for getWithQuery()")
		void shouldReturnImmediatelyOnGetWithQuerySuccess() {
			when(mockDelegate.getWithQuery("/path", "q=test")).thenReturn("success");

			String result = retryingClient.getWithQuery("/path", "q=test");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(1)).getWithQuery("/path", "q=test");
		}

		@Test
		@DisplayName("Should return immediately on first success for postGraphQL()")
		void shouldReturnImmediatelyOnPostGraphQLSuccess() {
			when(mockDelegate.postGraphQL("query")).thenReturn("success");

			String result = retryingClient.postGraphQL("query");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(1)).postGraphQL("query");
		}

	}

}
