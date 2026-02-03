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
 * Collection service for GitHub repository collaborators.
 *
 * <p>
 * Collects collaborator data including permissions for identifying maintainers. This is
 * used for Paper 1's label authority analysis - determining whether labels were applied
 * by project maintainers or external contributors.
 *
 * <p>
 * Unlike issues and PRs, collaborators don't use pagination or batch processing since
 * most repositories have fewer than 100 collaborators.
 */
public class CollaboratorsCollectionService extends BaseCollectionService<Collaborator> {

	private static final Logger logger = LoggerFactory.getLogger(CollaboratorsCollectionService.class);

	public CollaboratorsCollectionService(GraphQLService graphQLService, RestService restService,
			ObjectMapper objectMapper, CollectionProperties properties, CollectionStateRepository stateRepository,
			ArchiveService archiveService, BatchStrategy<Collaborator> batchStrategy) {
		super(graphQLService, restService, objectMapper, properties, stateRepository, archiveService, batchStrategy);
	}

	@Override
	protected String getCollectionType() {
		return "collaborators";
	}

	/**
	 * Collects GitHub repository collaborators.
	 *
	 * <p>
	 * This method fetches all collaborators for the specified repository in a single API
	 * call and saves them to a JSON file. Collaborators include their permissions which
	 * can be used to identify maintainers (users with push access or higher).
	 * @param request the collection request containing repository and options
	 * @return the collection result with counts and output file information
	 */
	@Override
	public CollectionResult collectItems(CollectionRequest request) {
		logger.info("Starting collaborators collection for repository: {}", request.repository());

		CollectionRequest validatedRequest = validateRequest(request);

		try {
			String[] repoParts = validatedRequest.repository().split("/");
			String owner = repoParts[0];
			String repo = repoParts[1];

			if (validatedRequest.dryRun()) {
				logger.info("DRY RUN: Would collect collaborators from {}/{}", owner, repo);
				return new CollectionResult(0, 0, "dry-run", List.of());
			}

			// Fetch collaborators via REST API
			List<Collaborator> collaborators = restService.getRepositoryCollaborators(owner, repo);
			logger.info("Found {} collaborators for {}/{}", collaborators.size(), owner, repo);

			if (collaborators.isEmpty()) {
				logger.warn("No collaborators found for {}/{}. This may indicate insufficient permissions.", owner,
						repo);
				return new CollectionResult(0, 0, "empty", List.of());
			}

			// Create output directory
			Path outputDir = stateRepository.createOutputDirectory("collaborators", request.repository(), "all");
			stateRepository.cleanOutputDirectory(outputDir);

			// Save collaborators to file
			String filename = saveCollaboratorsToFile(outputDir, collaborators, validatedRequest);

			// Create ZIP if requested
			List<String> batchFiles = List.of(filename);
			if (validatedRequest.zip()) {
				String archiveName = String.format("collaborators_%s", validatedRequest.repository().replace("/", "_"));
				archiveService.createArchive(outputDir, batchFiles, archiveName, false);
			}

			logger.info("Collaborators collection completed: {} collaborators", collaborators.size());
			return new CollectionResult(collaborators.size(), collaborators.size(), outputDir.toString(), batchFiles);

		}
		catch (Exception e) {
			logger.error("Collaborators collection failed for repository: {}", validatedRequest.repository(), e);
			throw new RuntimeException("Collaborators collection failed", e);
		}
	}

	/**
	 * Save collaborators to JSON file.
	 */
	private String saveCollaboratorsToFile(Path outputDir, List<Collaborator> collaborators,
			CollectionRequest request) {
		Map<String, Object> data = new HashMap<>();
		data.put("metadata", createMetadata(collaborators.size(), request));
		data.put("collaborators", collaborators);

		return stateRepository.saveBatch(outputDir, 1, data, "collaborators", false);
	}

	/**
	 * Create metadata for the collection.
	 */
	private Map<String, Object> createMetadata(int count, CollectionRequest request) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("collection_type", "collaborators");
		metadata.put("repository", request.repository());
		metadata.put("collaborator_count", count);
		metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		return metadata;
	}

	// ========== BaseCollectionService abstract methods (not used for collaborators)
	// ==========

	@Override
	protected int getTotalItemCount(String searchQuery) {
		// Not used - collaborators don't use search
		return 0;
	}

	@Override
	protected String buildSearchQuery(String owner, String repo, CollectionRequest request) {
		// Not used - collaborators don't use search
		return "";
	}

	@Override
	protected SearchResult<Collaborator> fetchBatch(String searchQuery, int batchSize, @Nullable String cursor) {
		// Not used - collaborators fetched in single API call
		return SearchResult.empty();
	}

	@Override
	protected List<Collaborator> processItemBatch(List<Collaborator> batch, String owner, String repo,
			CollectionRequest request) {
		// Not used - collaborators don't need additional processing
		return batch;
	}

	@Override
	protected String getItemTypeName() {
		return "collaborators";
	}

}
