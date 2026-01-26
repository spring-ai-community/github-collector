package org.springaicommunity.github.collector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.web.client.RestClient;

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
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class SimpleIntegrationIT {

	private GitHubRestService restService;

	private GitHubGraphQLService graphQLService;

	private IssueCollectionService collectionService;

	private ObjectMapper objectMapper;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

		String token = System.getenv("GITHUB_TOKEN");

		// Setup REST service
		GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
		RestClient restClient = RestClient.builder()
			.defaultHeader("Authorization", "token " + token)
			.defaultHeader("Accept", "application/vnd.github.v3+json")
			.build();
		restService = new GitHubRestService(gitHub, restClient, objectMapper);

		// Setup GraphQL service
		RestClient graphQLClient = RestClient.builder()
			.baseUrl("https://api.github.com/graphql")
			.defaultHeader("Authorization", "Bearer " + token)
			.defaultHeader("Content-Type", "application/json")
			.build();
		graphQLService = new GitHubGraphQLService(graphQLClient, objectMapper);

		// Setup collection service with minimal properties
		CollectionProperties properties = new CollectionProperties();
		properties.setBatchSize(3); // Very small for testing
		properties.setMaxRetries(1);
		properties.setRetryDelay(500);

		JsonNodeUtils jsonUtils = new JsonNodeUtils();
		collectionService = new IssueCollectionService(graphQLService, restService, jsonUtils, objectMapper,
				properties);
	}

	@Test
	@DisplayName("REST API connectivity test")
	void restApiConnectivityTest() {
		// Just verify we can get repository info
		assertThatCode(() -> {
			var repoInfo = restService.getRepositoryInfo("spring-projects", "spring-ai");
			assertThat(repoInfo).isNotNull();
			assertThat(repoInfo.has("name")).isTrue();
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
			CollectionResult result = collectionService.collectIssues(request);

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

}