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
 *
 * // For testing with mock HTTP client
 * GitHubClient mockClient = mock(GitHubClient.class);
 * IssueCollectionService testCollector = GitHubCollectorBuilder.create()
 *     .httpClient(mockClient)
 *     .buildIssueCollector();
 * }
 * </pre>
 */
public class GitHubCollectorBuilder {

	private String token;

	private CollectionProperties properties;

	private ObjectMapper objectMapper;

	private GitHubClient httpClient;

	private CollectionStateRepository stateRepository;

	private ArchiveService archiveService;

	private BatchStrategy batchStrategy;

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
	 * Set a custom GitHubClient implementation. Useful for testing with mocks or for
	 * adding decorators (caching, logging, retrying).
	 *
	 * <p>
	 * When a custom client is provided, the token is not required.
	 * @param httpClient custom GitHubClient implementation
	 * @return this builder
	 */
	public GitHubCollectorBuilder httpClient(GitHubClient httpClient) {
		this.httpClient = httpClient;
		return this;
	}

	/**
	 * Set a custom CollectionStateRepository implementation. Useful for testing with
	 * mocks or for alternative storage backends.
	 * @param stateRepository custom CollectionStateRepository implementation
	 * @return this builder
	 */
	public GitHubCollectorBuilder stateRepository(CollectionStateRepository stateRepository) {
		this.stateRepository = stateRepository;
		return this;
	}

	/**
	 * Set a custom ArchiveService implementation. Useful for testing with mocks or for
	 * alternative archive formats.
	 * @param archiveService custom ArchiveService implementation
	 * @return this builder
	 */
	public GitHubCollectorBuilder archiveService(ArchiveService archiveService) {
		this.archiveService = archiveService;
		return this;
	}

	/**
	 * Set a custom BatchStrategy implementation. Useful for testing with mocks or for
	 * alternative batching behaviors.
	 * @param batchStrategy custom BatchStrategy implementation
	 * @return this builder
	 */
	public GitHubCollectorBuilder batchStrategy(BatchStrategy batchStrategy) {
		this.batchStrategy = batchStrategy;
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
				components.objectMapper, properties, components.stateRepository, components.archiveService,
				components.batchStrategy);
	}

	/**
	 * Build a PRCollectionService.
	 * @return configured PRCollectionService
	 */
	public PRCollectionService buildPRCollector() {
		validateToken();
		Components components = buildComponents();
		return new PRCollectionService(components.graphQLService, components.restService, components.jsonNodeUtils,
				components.objectMapper, properties, components.stateRepository, components.archiveService,
				components.batchStrategy);
	}

	/**
	 * Build the RestService directly (for advanced usage).
	 * @return configured RestService
	 */
	public RestService buildRestService() {
		validateToken();
		return buildComponents().restService;
	}

	/**
	 * Build the GraphQLService directly (for advanced usage).
	 * @return configured GraphQLService
	 */
	public GraphQLService buildGraphQLService() {
		validateToken();
		return buildComponents().graphQLService;
	}

	private void validateToken() {
		// Skip token validation if a custom httpClient is provided
		if (httpClient != null) {
			return;
		}
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalStateException("GitHub token is required. Call token() or tokenFromEnv() first.");
		}
	}

	private Components buildComponents() {
		ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : createDefaultObjectMapper();
		GitHubClient client = this.httpClient != null ? this.httpClient : new GitHubHttpClient(token);
		GitHub gitHub = this.httpClient != null ? null : createGitHub();
		CollectionStateRepository repository = this.stateRepository != null ? this.stateRepository
				: new FileSystemStateRepository(mapper);
		ArchiveService archive = this.archiveService != null ? this.archiveService : new ZipArchiveService();
		BatchStrategy batch = this.batchStrategy != null ? this.batchStrategy : new FixedBatchStrategy();

		GitHubRestService restService = new GitHubRestService(gitHub, client, mapper);
		GitHubGraphQLService graphQLService = new GitHubGraphQLService(client, mapper);
		JsonNodeUtils jsonNodeUtils = new JsonNodeUtils();

		return new Components(restService, graphQLService, jsonNodeUtils, mapper, repository, archive, batch);
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
	private record Components(RestService restService, GraphQLService graphQLService, JsonNodeUtils jsonNodeUtils,
			ObjectMapper objectMapper, CollectionStateRepository stateRepository, ArchiveService archiveService,
			BatchStrategy batchStrategy) {
	}

}
