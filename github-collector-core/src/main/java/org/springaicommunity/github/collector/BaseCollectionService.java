package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for collection services providing common orchestration for issues and PRs.
 *
 * <p>
 * Uses generic type parameter to work with strongly-typed DTOs (Issue, PullRequest)
 * instead of raw JSON nodes.
 *
 * <p>
 * Delegates to extracted services for specific responsibilities:
 * <ul>
 * <li>{@link CollectionStateRepository} - file I/O operations</li>
 * <li>{@link ArchiveService} - ZIP archive creation</li>
 * <li>{@link BatchStrategy} - batch creation logic</li>
 * </ul>
 *
 * @param <T> the type of items being collected (e.g., Issue, PullRequest)
 */
public abstract class BaseCollectionService<T> {

	private static final Logger logger = LoggerFactory.getLogger(BaseCollectionService.class);

	protected final GraphQLService graphQLService;

	protected final RestService restService;

	protected final ObjectMapper objectMapper;

	protected final CollectionProperties properties;

	protected final CollectionStateRepository stateRepository;

	protected final ArchiveService archiveService;

	protected final BatchStrategy<T> batchStrategy;

	public BaseCollectionService(GraphQLService graphQLService, RestService restService, ObjectMapper objectMapper,
			CollectionProperties properties, CollectionStateRepository stateRepository, ArchiveService archiveService,
			BatchStrategy<T> batchStrategy) {
		this.graphQLService = graphQLService;
		this.restService = restService;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.stateRepository = stateRepository;
		this.archiveService = archiveService;
		this.batchStrategy = batchStrategy;
	}

	/**
	 * Main collection method to be implemented by subclasses
	 */
	public abstract CollectionResult collectItems(CollectionRequest request);

	/**
	 * Get the collection type name (e.g., "issues", "prs")
	 */
	protected abstract String getCollectionType();

	/**
	 * Get the total count of available items
	 */
	protected abstract int getTotalItemCount(String searchQuery);

	/**
	 * Build search query for the specific collection type
	 */
	protected abstract String buildSearchQuery(String owner, String repo, CollectionRequest request);

	/**
	 * Fetch a batch of items with pagination info.
	 * @param searchQuery the search query
	 * @param batchSize number of items to fetch
	 * @param cursor pagination cursor (null for first page)
	 * @return SearchResult containing typed items and pagination info
	 */
	protected abstract SearchResult<T> fetchBatch(String searchQuery, int batchSize, @Nullable String cursor);

	/**
	 * Process items in current batch (e.g., enhance with additional data)
	 */
	protected abstract List<T> processItemBatch(List<T> batch, String owner, String repo, CollectionRequest request);

	/**
	 * Get item type name for logging (e.g., "issues", "PRs")
	 */
	protected abstract String getItemTypeName();

	/**
	 * Template method for collecting items in batches with shared pagination logic.
	 */
	protected CollectionResult collectItemsInBatches(String owner, String repo, CollectionRequest request,
			Path outputDir, String searchQuery, int totalAvailableItems) throws Exception {
		List<String> batchFiles = new ArrayList<>();
		String cursor = null;
		int batchNum = 1;
		boolean hasMoreFromAPI = true;
		AtomicInteger processedCount = new AtomicInteger(0);

		int targetBatchSize = request.batchSize();
		boolean isDashboardMode = request.maxIssues() != null;
		int effectiveTotal = isDashboardMode ? Math.min(totalAvailableItems, request.maxIssues()) : totalAvailableItems;
		int fetchSize = isDashboardMode ? Math.min(request.maxIssues(), 100) : Math.max(targetBatchSize, 100);

		List<T> pendingItems = new ArrayList<>();

		while (hasMoreFromAPI || !pendingItems.isEmpty()) {
			// Check if we've reached the maxIssues limit in dashboard mode
			if (isDashboardMode && processedCount.get() >= effectiveTotal) {
				logger.info("Dashboard mode: reached target of {} {}, stopping collection", effectiveTotal,
						getItemTypeName());
				break;
			}

			// Fetch more items if needed
			if (pendingItems.size() < targetBatchSize && hasMoreFromAPI) {
				int remainingToFetch = isDashboardMode ? effectiveTotal - processedCount.get() : fetchSize;
				int actualFetchSize = Math.min(fetchSize, remainingToFetch);

				if (actualFetchSize <= 0)
					break;

				logger.info("Fetching {} from API (cursor: {}, fetch size: {}, dashboard mode: {})", getItemTypeName(),
						cursor != null ? "present" : "null", actualFetchSize, isDashboardMode);

				// Fetch batch using typed SearchResult
				SearchResult<T> searchResult = fetchBatch(searchQuery, actualFetchSize, cursor);

				// Update pagination
				hasMoreFromAPI = searchResult.hasMore();
				cursor = searchResult.nextCursor();

				// Add to pending items
				pendingItems.addAll(searchResult.items());

				logger.info("Fetched {} {}, {} pending, dashboard limit: {}", searchResult.items().size(),
						getItemTypeName(), pendingItems.size(), isDashboardMode ? effectiveTotal : "unlimited");
			}

			// Create batch, respecting dashboard limits
			int maxBatchSize = isDashboardMode ? Math.min(targetBatchSize, effectiveTotal - processedCount.get())
					: targetBatchSize;
			List<T> currentBatch = batchStrategy.createBatch(pendingItems, maxBatchSize);

			if (currentBatch.isEmpty()) {
				break;
			}

			// Process items (e.g., enhance PRs with soft approval detection)
			List<T> processedItems = processItemBatch(currentBatch, owner, repo, request);

			// Save batch to file
			String filename = saveBatchToFile(outputDir, batchNum, processedItems, request);
			batchFiles.add(filename);

			// Update counters
			processedCount.addAndGet(processedItems.size());
			batchNum++;

			logger.info("Batch {}: Processed {} {}, total: {}/{}", batchNum - 1, processedItems.size(),
					getItemTypeName(), processedCount.get(), isDashboardMode ? effectiveTotal : totalAvailableItems);
		}

		// Create ZIP if requested
		createZipFile(outputDir, batchFiles, request);

		String itemTypeName = getItemTypeName();
		String capitalizedTypeName = itemTypeName.substring(0, 1).toUpperCase() + itemTypeName.substring(1);
		logger.info("{} collection completed: {}/{} {} processed", capitalizedTypeName, processedCount.get(),
				totalAvailableItems, itemTypeName);

		return new CollectionResult(totalAvailableItems, processedCount.get(), outputDir.toString(), batchFiles);
	}

	/**
	 * Common validation logic for collection requests
	 */
	protected CollectionRequest validateRequest(CollectionRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Collection request cannot be null");
		}

		String repository = request.repository();
		if (repository == null || repository.trim().isEmpty()) {
			repository = properties.getDefaultRepository();
			if (repository == null || repository.trim().isEmpty()) {
				throw new IllegalArgumentException("Repository must be specified either in request or configuration");
			}
		}

		int batchSize = request.batchSize() <= 0 ? properties.getBatchSize() : request.batchSize();

		String state = request.issueState();
		if (state == null || state.trim().isEmpty()) {
			state = properties.getDefaultState();
		}

		String labelMode = request.labelMode();
		if (labelMode == null || labelMode.trim().isEmpty()) {
			labelMode = properties.getDefaultLabelMode();
		}

		return new CollectionRequest(repository, batchSize, request.dryRun(), request.incremental(), request.zip(),
				request.clean(), request.resume(), state, request.labelFilters(), labelMode, request.maxIssues(),
				request.sortBy(), request.sortOrder(), request.collectionType(), request.prNumber(), request.prState(),
				request.verbose());
	}

	/**
	 * Create output directory structure
	 */
	protected Path createOutputDirectory(CollectionRequest request) {
		String state = getCollectionType().equals("prs") ? request.prState() : request.issueState();
		return stateRepository.createOutputDirectory(getCollectionType(), request.repository(), state);
	}

	/**
	 * Clean existing output directory if requested
	 */
	protected void cleanOutputDirectory(Path outputDir, boolean clean) {
		if (!clean) {
			return;
		}
		stateRepository.cleanOutputDirectory(outputDir);
	}

	/**
	 * Save batch data to file
	 */
	protected String saveBatchToFile(Path outputDir, int batchIndex, List<T> items, CollectionRequest request) {
		Map<String, Object> batchData = new HashMap<>();
		batchData.put("metadata", createBatchMetadata(batchIndex, items.size(), request));
		batchData.put(getCollectionType(), items);

		return stateRepository.saveBatch(outputDir, batchIndex, batchData, getCollectionType(), request.dryRun());
	}

	/**
	 * Create metadata for a batch
	 */
	protected Map<String, Object> createBatchMetadata(int batchIndex, int itemCount, CollectionRequest request) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("batch_index", batchIndex);
		metadata.put("item_count", itemCount);
		metadata.put("collection_type", getCollectionType());
		metadata.put("repository", request.repository());
		String state = getCollectionType().equals("prs") ? request.prState() : request.issueState();
		metadata.put("state", state);
		metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

		if (request.labelFilters() != null && !request.labelFilters().isEmpty()) {
			metadata.put("label_filters", request.labelFilters());
			metadata.put("label_mode", request.labelMode());
		}

		return metadata;
	}

	/**
	 * Create ZIP file from batch files
	 */
	protected void createZipFile(Path outputDir, List<String> batchFiles, CollectionRequest request) {
		if (!request.zip()) {
			return;
		}

		String state = getCollectionType().equals("prs") ? request.prState() : request.issueState();
		String archiveName = String.format("%s_%s_%s", getCollectionType(), request.repository().replace("/", "_"),
				state);
		archiveService.createArchive(outputDir, batchFiles, archiveName, request.dryRun());
	}

	/**
	 * Log collection progress
	 */
	protected void logProgress(int currentBatch, int totalProcessed, int totalAvailable, String searchQuery) {
		double percentage = totalAvailable > 0 ? (double) totalProcessed / totalAvailable * 100 : 0;
		logger.info("Progress: Batch {} - {}/{} {} processed ({:.1f}%)", currentBatch, totalProcessed, totalAvailable,
				getCollectionType(), percentage);

		if (logger.isDebugEnabled()) {
			logger.debug("Search query: {}", searchQuery);
		}
	}

	/**
	 * Handle collection errors with retry logic
	 */
	protected void handleCollectionError(Exception e, int attempt, int maxRetries) {
		logger.error("Collection attempt {} failed: {}", attempt, e.getMessage());

		if (attempt < maxRetries) {
			int delaySeconds = (int) Math.pow(2, attempt);
			logger.info("Retrying in {} seconds... (attempt {}/{})", delaySeconds, attempt + 1, maxRetries);

			try {
				Thread.sleep(delaySeconds * 1000L);
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Collection interrupted", ie);
			}
		}
		else {
			throw new RuntimeException("Collection failed after " + maxRetries + " attempts", e);
		}
	}

}
