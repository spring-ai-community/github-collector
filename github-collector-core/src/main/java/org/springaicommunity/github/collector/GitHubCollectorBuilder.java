package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

/**
 * Builder for creating GitHub collector services without Spring dependencies.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * // Simple usage with environment variable
 * IssueCollectionService collector = GitHubCollectorBuilder.create()
 *     .tokenFromEnv()
 *     .buildIssueCollector();
 *
 * // With custom configuration
 * CollectionProperties props = new CollectionProperties();
 * props.setBatchSize(50);
 *
 * IssueCollectionService collector = GitHubCollectorBuilder.create()
 *     .token("ghp_xxxxx")
 *     .properties(props)
 *     .buildIssueCollector();
 *
 * // Collect issues
 * CollectionRequest request = new CollectionRequest("spring-projects/spring-ai", ...);
 * CollectionResult result = collector.collectIssues(request);
 * }
 * </pre>
 */
public class GitHubCollectorBuilder {

	private String token;

	private CollectionProperties properties;

	private ObjectMapper objectMapper;

	private GitHubCollectorBuilder() {
		this.properties = new CollectionProperties();
	}

	/**
	 * Create a new builder instance.
	 * @return new GitHubCollectorBuilder
	 */
	public static GitHubCollectorBuilder create() {
		return new GitHubCollectorBuilder();
	}

	/**
	 * Set the GitHub token directly.
	 * @param token GitHub personal access token
	 * @return this builder
	 */
	public GitHubCollectorBuilder token(String token) {
		this.token = token;
		return this;
	}

	/**
	 * Read the GitHub token from the GITHUB_TOKEN environment variable.
	 * @return this builder
	 * @throws IllegalStateException if GITHUB_TOKEN is not set
	 */
	public GitHubCollectorBuilder tokenFromEnv() {
		this.token = System.getenv("GITHUB_TOKEN");
		if (this.token == null || this.token.trim().isEmpty()) {
			throw new IllegalStateException(
					"GITHUB_TOKEN environment variable is required. Please set your GitHub personal access token.");
		}
		return this;
	}

	/**
	 * Set collection properties.
	 * @param properties configuration properties
	 * @return this builder
	 */
	public GitHubCollectorBuilder properties(CollectionProperties properties) {
		this.properties = properties;
		return this;
	}

	/**
	 * Set a custom ObjectMapper.
	 * @param objectMapper Jackson ObjectMapper
	 * @return this builder
	 */
	public GitHubCollectorBuilder objectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		return this;
	}

	/**
	 * Build an IssueCollectionService.
	 * @return configured IssueCollectionService
	 */
	public IssueCollectionService buildIssueCollector() {
		validateToken();
		Components components = buildComponents();
		return new IssueCollectionService(components.graphQLService, components.restService, components.jsonNodeUtils,
				components.objectMapper, properties);
	}

	/**
	 * Build a PRCollectionService.
	 * @return configured PRCollectionService
	 */
	public PRCollectionService buildPRCollector() {
		validateToken();
		Components components = buildComponents();
		return new PRCollectionService(components.graphQLService, components.restService, components.jsonNodeUtils,
				components.objectMapper, properties);
	}

	/**
	 * Build the GitHubRestService directly (for advanced usage).
	 * @return configured GitHubRestService
	 */
	public GitHubRestService buildRestService() {
		validateToken();
		return buildComponents().restService;
	}

	/**
	 * Build the GitHubGraphQLService directly (for advanced usage).
	 * @return configured GitHubGraphQLService
	 */
	public GitHubGraphQLService buildGraphQLService() {
		validateToken();
		return buildComponents().graphQLService;
	}

	private void validateToken() {
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalStateException("GitHub token is required. Call token() or tokenFromEnv() first.");
		}
	}

	private Components buildComponents() {
		ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : createDefaultObjectMapper();
		GitHubHttpClient httpClient = new GitHubHttpClient(token);
		GitHub gitHub = createGitHub();

		GitHubRestService restService = new GitHubRestService(gitHub, httpClient, mapper);
		GitHubGraphQLService graphQLService = new GitHubGraphQLService(httpClient, mapper);
		JsonNodeUtils jsonNodeUtils = new JsonNodeUtils();

		return new Components(restService, graphQLService, jsonNodeUtils, mapper);
	}

	private ObjectMapper createDefaultObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		return mapper;
	}

	private GitHub createGitHub() {
		try {
			return new GitHubBuilder().withOAuthToken(token).build();
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to create GitHub client: " + e.getMessage(), e);
		}
	}

	/**
	 * Internal record to hold built components.
	 */
	private record Components(GitHubRestService restService, GitHubGraphQLService graphQLService,
			JsonNodeUtils jsonNodeUtils, ObjectMapper objectMapper) {
	}

}
