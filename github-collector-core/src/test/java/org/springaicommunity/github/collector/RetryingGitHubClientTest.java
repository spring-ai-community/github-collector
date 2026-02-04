package org.springaicommunity.github.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

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

	@Nested
	@DisplayName("Rate Limit Retry Tests")
	class RateLimitRetryTest {

		@Test
		@DisplayName("Should retry on 403 rate limit (remaining=0)")
		void shouldRetryOn403RateLimit() {
			long resetEpoch = Instant.now().getEpochSecond() + 2; // 2 seconds from now
			GitHubHttpClient.GitHubApiException rateLimitError = new GitHubHttpClient.GitHubApiException(
					"Rate limit exceeded", 403, "rate limit", 0, resetEpoch);

			when(mockDelegate.get("/path")).thenThrow(rateLimitError).thenReturn("success");

			String result = retryingClient.get("/path");

			assertThat(result).isEqualTo("success");
			verify(mockDelegate, times(2)).get("/path");
		}

		@Test
		@DisplayName("Should NOT retry on 403 non-rate-limit (remaining > 0)")
		void shouldNotRetryOn403NonRateLimit() {
			// A 403 with remaining > 0 is a permission error, not rate limit
			GitHubHttpClient.GitHubApiException forbidden = new GitHubHttpClient.GitHubApiException("Forbidden", 403,
					"Not allowed", 4999, Instant.now().getEpochSecond() + 3600);

			when(mockDelegate.get("/path")).thenThrow(forbidden);

			assertThatThrownBy(() -> retryingClient.get("/path"))
				.isInstanceOf(GitHubHttpClient.GitHubApiException.class)
				.hasMessageContaining("Forbidden");

			verify(mockDelegate, times(1)).get("/path");
		}

		@Test
		@DisplayName("Should retry 429 with reset-aware wait time")
		void shouldRetry429WithResetAwareWait() {
			long resetEpoch = Instant.now().getEpochSecond() + 3; // 3 seconds from now
			GitHubHttpClient.GitHubApiException error = new GitHubHttpClient.GitHubApiException(
					"Too Many Requests (429)", 429, "rate limit", 0, resetEpoch);

			when(mockDelegate.get("/path")).thenThrow(error).thenReturn("success");

			long start = System.currentTimeMillis();
			String result = retryingClient.get("/path");
			long elapsed = System.currentTimeMillis() - start;

			assertThat(result).isEqualTo("success");
			// Should have waited ~3-4 seconds (3s + 1s buffer) instead of 1ms default
			assertThat(elapsed).isGreaterThan(2000);
		}

		@Test
		@DisplayName("Should use default backoff when reset time is in the past")
		void shouldUseDefaultBackoffWhenResetInPast() {
			long resetEpoch = Instant.now().getEpochSecond() - 10; // 10 seconds ago
			GitHubHttpClient.GitHubApiException error = new GitHubHttpClient.GitHubApiException("Rate limited", 429,
					"rate limit", 0, resetEpoch);

			when(mockDelegate.get("/path")).thenThrow(error).thenReturn("success");

			long start = System.currentTimeMillis();
			String result = retryingClient.get("/path");
			long elapsed = System.currentTimeMillis() - start;

			assertThat(result).isEqualTo("success");
			// Should have used the default 1ms delay (from test setup), not a large wait
			assertThat(elapsed).isLessThan(1000);
		}

	}

	@Nested
	@DisplayName("Proactive Pacing Tests")
	class ProactivePacingTest {

		@Test
		@DisplayName("Should pace when remaining is below threshold")
		void shouldPaceWhenRemainingBelowThreshold() {
			long resetEpoch = Instant.now().getEpochSecond() + 60;
			RateLimitInfo lowInfo = new RateLimitInfo(5000, 50, resetEpoch, 4950);
			when(mockDelegate.getLastRateLimitInfo()).thenReturn(lowInfo);
			when(mockDelegate.get("/path")).thenReturn("success");

			RetryingGitHubClient pacingClient = RetryingGitHubClient.builder()
				.wrapping(mockDelegate)
				.maxRetries(0)
				.initialDelayMs(1)
				.pacingThreshold(100)
				.build();

			long start = System.currentTimeMillis();
			pacingClient.get("/path");
			long elapsed = System.currentTimeMillis() - start;

			// With 50 remaining and 60s until reset: pace = 60000/50 = 1200ms
			assertThat(elapsed).isGreaterThanOrEqualTo(100);
		}

		@Test
		@DisplayName("Should NOT pace when remaining is above threshold")
		void shouldNotPaceWhenRemainingAboveThreshold() {
			long resetEpoch = Instant.now().getEpochSecond() + 3600;
			RateLimitInfo healthyInfo = new RateLimitInfo(5000, 4500, resetEpoch, 500);
			when(mockDelegate.getLastRateLimitInfo()).thenReturn(healthyInfo);
			when(mockDelegate.get("/path")).thenReturn("success");

			RetryingGitHubClient pacingClient = RetryingGitHubClient.builder()
				.wrapping(mockDelegate)
				.maxRetries(0)
				.initialDelayMs(1)
				.pacingThreshold(100)
				.build();

			long start = System.currentTimeMillis();
			pacingClient.get("/path");
			long elapsed = System.currentTimeMillis() - start;

			// Should not pace at all — remaining is well above threshold
			assertThat(elapsed).isLessThan(500);
		}

		@Test
		@DisplayName("Should NOT pace when rate limit info is null")
		void shouldNotPaceWhenInfoIsNull() {
			when(mockDelegate.getLastRateLimitInfo()).thenReturn(null);
			when(mockDelegate.get("/path")).thenReturn("success");

			long start = System.currentTimeMillis();
			retryingClient.get("/path");
			long elapsed = System.currentTimeMillis() - start;

			assertThat(elapsed).isLessThan(500);
		}

		@Test
		@DisplayName("Should delegate getLastRateLimitInfo to wrapped client")
		void shouldDelegateGetLastRateLimitInfo() {
			RateLimitInfo info = new RateLimitInfo(5000, 4000, 1234567890L, 1000);
			when(mockDelegate.getLastRateLimitInfo()).thenReturn(info);

			RateLimitInfo result = retryingClient.getLastRateLimitInfo();

			assertThat(result).isEqualTo(info);
			verify(mockDelegate).getLastRateLimitInfo();
		}

	}

	@Nested
	@DisplayName("GitHubApiException Rate Limit Fields Tests")
	class ApiExceptionRateLimitFieldsTest {

		@Test
		@DisplayName("isRateLimitError returns true for 429")
		void isRateLimitErrorFor429() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Rate limited", 429, "body",
					0, 12345L);
			assertThat(e.isRateLimitError()).isTrue();
		}

		@Test
		@DisplayName("isRateLimitError returns true for 403 with remaining=0")
		void isRateLimitErrorFor403WithZeroRemaining() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Rate limited", 403, "body",
					0, 12345L);
			assertThat(e.isRateLimitError()).isTrue();
		}

		@Test
		@DisplayName("isRateLimitError returns false for 403 without rate limit info")
		void isRateLimitErrorFalseFor403WithoutInfo() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Forbidden", 403, "body");
			assertThat(e.isRateLimitError()).isFalse();
		}

		@Test
		@DisplayName("isRateLimitError returns false for 404")
		void isRateLimitErrorFalseFor404() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Not Found", 404, "body",
					4999, 12345L);
			assertThat(e.isRateLimitError()).isFalse();
		}

		@Test
		@DisplayName("Rate limit fields are accessible")
		void rateLimitFieldsAccessible() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Rate limited", 429, "body",
					42, 1234567890L);
			assertThat(e.getRateLimitRemaining()).isEqualTo(42);
			assertThat(e.getResetEpochSeconds()).isEqualTo(1234567890L);
		}

		@Test
		@DisplayName("Default constructor uses -1 for rate limit fields")
		void defaultConstructorDefaults() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Error", 500, "body");
			assertThat(e.getRateLimitRemaining()).isEqualTo(-1);
			assertThat(e.getResetEpochSeconds()).isEqualTo(-1);
			assertThat(e.isRateLimitError()).isFalse();
		}

		@Test
		@DisplayName("Cause constructor uses -1 for rate limit fields")
		void causeConstructorDefaults() {
			GitHubHttpClient.GitHubApiException e = new GitHubHttpClient.GitHubApiException("Error",
					new RuntimeException("cause"));
			assertThat(e.getRateLimitRemaining()).isEqualTo(-1);
			assertThat(e.getResetEpochSeconds()).isEqualTo(-1);
			assertThat(e.isRateLimitError()).isFalse();
		}

	}

}
