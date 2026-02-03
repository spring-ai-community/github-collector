package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection service for GitHub repository releases.
 *
 * <p>
 * Collects release data including release notes for H4 (External Validity) analysis -
 * validating that issues mentioned in release notes match their labels (e.g., issues in
 * "Bug Fixes" section should have the bug label).
 *
 * <p>
 * Unlike issues and PRs, releases don't use pagination or batch processing since most
 * repositories have fewer than 100 releases.
 */
public class ReleasesCollectionService extends BaseCollectionService<Release> {

	private static final Logger logger = LoggerFactory.getLogger(ReleasesCollectionService.class);

	public ReleasesCollectionService(GraphQLService graphQLService, RestService restService, ObjectMapper objectMapper,
			CollectionProperties properties, CollectionStateRepository stateRepository, ArchiveService archiveService,
			BatchStrategy<Release> batchStrategy) {
		super(graphQLService, restService, objectMapper, properties, stateRepository, archiveService, batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "releases";
	}

	/**
	 * Collects GitHub repository releases.
	 *
	 * <p>
	 * This method fetches all releases for the specified repository in a single API call
	 * and saves them to a JSON file. Releases include the release notes body which can be
	 * parsed to extract issue and PR references.
	 * @param request the collection request containing repository and options
	 * @return the collection result with counts and output file information
	 */
	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting releases collection for repository: {}", request.repository());

		CollectionRequest validatedRequest = validateRequest(request);

		try {
			String[] repoParts = validatedRequest.repository().split("/");
			String owner = repoParts[0];
			String repo = repoParts[1];

			if (validatedRequest.dryRun()) {
				logger.info("DRY RUN: Would collect releases from {}/{}", owner, repo);
				return new CollectionResult(0, 0, "dry-run", List.of());
			}

			// Fetch releases via REST API
			List<Release> releases = restService.getRepositoryReleases(owner, repo);
			logger.info("Found {} releases for {}/{}", releases.size(), owner, repo);

			if (releases.isEmpty()) {
				logger.info("No releases found for {}/{}.", owner, repo);
				return new CollectionResult(0, 0, "empty", List.of());
			}

			// Create output directory
			Path outputDir = stateRepository.createOutputDirectory("releases", request.repository(), "all");
			stateRepository.cleanOutputDirectory(outputDir);

			// Save releases to file
			String filename = saveReleasesToFile(outputDir, releases, validatedRequest);

			// Create ZIP if requested
			List<String> batchFiles = List.of(filename);
			if (validatedRequest.zip()) {
				String archiveName = String.format("releases_%s", validatedRequest.repository().replace("/", "_"));
				archiveService.createArchive(outputDir, batchFiles, archiveName, false);
			}

			logger.info("Releases collection completed: {} releases", releases.size());
			return new CollectionResult(releases.size(), releases.size(), outputDir.toString(), batchFiles);

		}
		catch (Exception e) {
			logger.error("Releases collection failed for repository: {}", validatedRequest.repository(), e);
			throw new RuntimeException("Releases collection failed", e);
		}
	}

	/**
	 * Save releases to JSON file.
	 */
	private String saveReleasesToFile(Path outputDir, List<Release> releases, CollectionRequest request) {
		Map<String, Object> data = new HashMap<>();
		data.put("metadata", createMetadata(releases.size(), request));
		data.put("releases", releases);

		return stateRepository.saveBatch(outputDir, 1, data, "releases", false);
	}

	/**
	 * Create metadata for the collection.
	 */
	private Map<String, Object> createMetadata(int count, CollectionRequest request) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("collection_type", "releases");
		metadata.put("repository", request.repository());
		metadata.put("release_count", count);
		metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		return metadata;
	}

	// ========== BaseCollectionService abstract methods (not used for releases)
	// ==========

	@Override
	protected int getTotalItemCount(String searchQuery) {
		// Not used - releases don't use search
		return 0;
	}

	@Override
	protected String buildSearchQuery(String owner, String repo, CollectionRequest request) {
		// Not used - releases don't use search
		return "";
	}

	@Override
	protected SearchResult<Release> fetchBatch(String searchQuery, int batchSize, @Nullable String cursor) {
		// Not used - releases fetched in single API call
		return SearchResult.empty();
	}

	@Override
	protected List<Release> processItemBatch(List<Release> batch, String owner, String repo,
			CollectionRequest request) {
		// Not used - releases don't need additional processing
		return batch;
	}

	@Override
	protected String getItemTypeName() {
		return "releases";
	}

}
