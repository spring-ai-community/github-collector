package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.Nullable;

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

	private BatchStrategy<?> batchStrategy;

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
	 * @param properties configuration properties (null to use defaults)
	 * @return this builder
	 */
	public GitHubCollectorBuilder properties(@Nullable CollectionProperties properties) {
		if (properties != null) {
			this.properties = properties;
		}
		return this;
	}

	/**
	 * Set a custom ObjectMapper.
	 * @param objectMapper Jackson ObjectMapper (null to use default)
	 * @return this builder
	 */
	public GitHubCollectorBuilder objectMapper(@Nullable ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		return this;
	}

	/**
	 * Set a custom GitHubClient implementation. Useful for testing with mocks or for
	 * adding decorators (caching, logging, retrying).
	 *
	 * <p>
	 * When a custom client is provided, the token is not required.
	 * @param httpClient custom GitHubClient implementation (null to use default)
	 * @return this builder
	 */
	public GitHubCollectorBuilder httpClient(@Nullable GitHubClient httpClient) {
		this.httpClient = httpClient;
		return this;
	}

	/**
	 * Set a custom CollectionStateRepository implementation. Useful for testing with
	 * mocks or for alternative storage backends.
	 * @param stateRepository custom CollectionStateRepository implementation (null to use
	 * default)
	 * @return this builder
	 */
	public GitHubCollectorBuilder stateRepository(@Nullable CollectionStateRepository stateRepository) {
		this.stateRepository = stateRepository;
		return this;
	}

	/**
	 * Set a custom ArchiveService implementation. Useful for testing with mocks or for
	 * alternative archive formats.
	 * @param archiveService custom ArchiveService implementation (null to use default)
	 * @return this builder
	 */
	public GitHubCollectorBuilder archiveService(@Nullable ArchiveService archiveService) {
		this.archiveService = archiveService;
		return this;
	}

	/**
	 * Set a custom BatchStrategy implementation. Useful for testing with mocks or for
	 * alternative batching behaviors.
	 * @param batchStrategy custom BatchStrategy implementation (null to use default)
	 * @return this builder
	 */
	public GitHubCollectorBuilder batchStrategy(@Nullable BatchStrategy<?> batchStrategy) {
		this.batchStrategy = batchStrategy;
		return this;
	}

	/**
	 * Build an IssueCollectionService.
	 * @return configured IssueCollectionService
	 */
	@SuppressWarnings("unchecked")
	public IssueCollectionService buildIssueCollector() {
		validateToken();
		Components components = buildComponents();
		return new IssueCollectionService(components.graphQLService, components.restService, components.objectMapper,
				properties, components.stateRepository, components.archiveService,
				(BatchStrategy<Issue>) components.batchStrategy);
	}

	/**
	 * Build a PRCollectionService.
	 * @return configured PRCollectionService
	 */
	@SuppressWarnings("unchecked")
	public PRCollectionService buildPRCollector() {
		validateToken();
		Components components = buildComponents();
		return new PRCollectionService(components.graphQLService, components.restService, components.objectMapper,
				properties, components.stateRepository, components.archiveService,
				(BatchStrategy<AnalyzedPullRequest>) components.batchStrategy);
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
		CollectionStateRepository repository = this.stateRepository != null ? this.stateRepository
				: new FileSystemStateRepository(mapper);
		ArchiveService archive = this.archiveService != null ? this.archiveService : new ZipArchiveService();
		BatchStrategy<?> batch = this.batchStrategy != null ? this.batchStrategy : new FixedBatchStrategy<>();

		GitHubRestService restService = new GitHubRestService(client, mapper);
		GitHubGraphQLService graphQLService = new GitHubGraphQLService(client, mapper);

		return new Components(restService, graphQLService, mapper, repository, archive, batch);
	}

	private ObjectMapper createDefaultObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		return mapper;
	}

	/**
	 * Internal record to hold built components.
	 */
	private record Components(RestService restService, GraphQLService graphQLService, ObjectMapper objectMapper,
			CollectionStateRepository stateRepository, ArchiveService archiveService, BatchStrategy<?> batchStrategy) {
	}

}
