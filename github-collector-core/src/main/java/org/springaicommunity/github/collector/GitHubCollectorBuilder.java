package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

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
		this.token = EnvironmentSupport.get("GITHUB_TOKEN");
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
	 * Build a CollaboratorsCollectionService.
	 * @return configured CollaboratorsCollectionService
	 */
	@SuppressWarnings("unchecked")
	public CollaboratorsCollectionService buildCollaboratorsCollector() {
		validateToken();
		Components components = buildComponents();
		return new CollaboratorsCollectionService(components.graphQLService, components.restService,
				components.objectMapper, properties, components.stateRepository, components.archiveService,
				(BatchStrategy<Collaborator>) components.batchStrategy);
	}

	/**
	 * Build a ReleasesCollectionService.
	 * @return configured ReleasesCollectionService
	 */
	@SuppressWarnings("unchecked")
	public ReleasesCollectionService buildReleasesCollector() {
		validateToken();
		Components components = buildComponents();
		return new ReleasesCollectionService(components.graphQLService, components.restService, components.objectMapper,
				properties, components.stateRepository, components.archiveService,
				(BatchStrategy<Release>) components.batchStrategy);
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

	/**
	 * Build a WindowedCollectionService wrapping an IssueCollectionService.
	 *
	 * <p>
	 * The returned service automatically splits collection into time windows when the
	 * request specifies a date range that would exceed the GitHub Search API's 1,000
	 * result limit.
	 * @param request the collection request (used to build the count query)
	 * @return WindowedCollectionService wrapping the issue collector
	 */
	@SuppressWarnings("unchecked")
	public WindowedCollectionService<Issue> buildWindowedIssueCollector(CollectionRequest request) {
		validateToken();
		Components components = buildComponents();
		IssueCollectionService issueCollector = new IssueCollectionService(components.graphQLService,
				components.restService, components.objectMapper, properties, components.stateRepository,
				components.archiveService, (BatchStrategy<Issue>) components.batchStrategy);

		AdaptiveWindowPlanner planner = new AdaptiveWindowPlanner();

		BiFunction<String, String, Integer> countFn = (after, before) -> {
			String query = buildIssueSearchQuery(request.repository(), request.issueState(), request.labelFilters(),
					request.labelMode(), after, before);
			return components.graphQLService.getSearchIssueCount(query);
		};

		return new WindowedCollectionService<>(issueCollector, planner, countFn);
	}

	/**
	 * Build a WindowedCollectionService wrapping a PRCollectionService.
	 *
	 * <p>
	 * The returned service automatically splits collection into time windows when the
	 * request specifies a date range that would exceed the GitHub Search API's 1,000
	 * result limit.
	 * @param request the collection request (used to build the count query)
	 * @return WindowedCollectionService wrapping the PR collector
	 */
	@SuppressWarnings("unchecked")
	public WindowedCollectionService<AnalyzedPullRequest> buildWindowedPRCollector(CollectionRequest request) {
		validateToken();
		Components components = buildComponents();
		PRCollectionService prCollector = new PRCollectionService(components.graphQLService, components.restService,
				components.objectMapper, properties, components.stateRepository, components.archiveService,
				(BatchStrategy<AnalyzedPullRequest>) components.batchStrategy);

		AdaptiveWindowPlanner planner = new AdaptiveWindowPlanner();

		BiFunction<String, String, Integer> countFn = (after, before) -> {
			String query = components.restService.buildPRSearchQuery(request.repository(), request.prState(),
					request.labelFilters(), request.labelMode(), after, before);
			return components.restService.getTotalPRCount(query);
		};

		return new WindowedCollectionService<>(prCollector, planner, countFn);
	}

	/**
	 * Build an issue search query string with date range. This mirrors the query format
	 * used internally by IssueCollectionService.
	 */
	public static String buildIssueSearchQuery(String repository, String state, List<String> labels, String labelMode,
			@Nullable String createdAfter, @Nullable String createdBefore) {
		StringBuilder query = new StringBuilder();
		query.append("repo:").append(repository).append(" is:issue");

		if (state != null) {
			switch (state.toLowerCase()) {
				case "open":
					query.append(" is:open");
					break;
				case "closed":
					query.append(" is:closed");
					break;
				case "all":
					break;
			}
		}

		if (labels != null && !labels.isEmpty()) {
			for (String label : labels) {
				query.append(" label:\"").append(label.trim()).append("\"");
			}
		}

		if (createdAfter != null && createdBefore != null) {
			query.append(" created:").append(createdAfter).append("..").append(createdBefore);
		}
		else if (createdAfter != null) {
			query.append(" created:>=").append(createdAfter);
		}
		else if (createdBefore != null) {
			query.append(" created:<").append(createdBefore);
		}

		return query.toString();
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
		GitHubClient rawClient = this.httpClient != null ? this.httpClient : new GitHubHttpClient(token);

		// Wrap with retry + rate limit handling unless a custom client was provided
		GitHubClient client;
		if (this.httpClient != null) {
			// User provided a custom client — respect it as-is (may already have retry)
			client = rawClient;
		}
		else {
			client = RetryingGitHubClient.builder().wrapping(rawClient).maxRetries(3).build();
		}

		CollectionStateRepository repository = this.stateRepository != null ? this.stateRepository
				: new FileSystemStateRepository(mapper);
		ArchiveService archive = this.archiveService != null ? this.archiveService : new ZipArchiveService();
		BatchStrategy<?> batch = this.batchStrategy != null ? this.batchStrategy : new FixedBatchStrategy<>();

		GitHubRestService restService = new GitHubRestService(client, mapper);
		GitHubGraphQLService graphQLService = new GitHubGraphQLService(client, mapper);

		return new Components(restService, graphQLService, mapper, repository, archive, batch);
	}

	private ObjectMapper createDefaultObjectMapper() {
		return ObjectMapperFactory.create();
	}

	/**
	 * Internal record to hold built components.
	 */
	private record Components(RestService restService, GraphQLService graphQLService, ObjectMapper objectMapper,
			CollectionStateRepository stateRepository, ArchiveService archiveService, BatchStrategy<?> batchStrategy) {
	}

}
