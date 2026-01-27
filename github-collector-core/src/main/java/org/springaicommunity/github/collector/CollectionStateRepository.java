package org.springaicommunity.github.collector;

import java.nio.file.Path;
import java.util.Map;

/**
 * Repository interface for collection state persistence operations.
 *
 * <p>
 * Abstracts file system operations to enable testability and potential alternative
 * storage implementations.
 */
public interface CollectionStateRepository {

	/**
	 * Create the output directory structure for a collection.
	 * @param collectionType type of collection (e.g., "issues", "prs")
	 * @param repository repository in "owner/repo" format
	 * @param state the state filter (e.g., "open", "closed", "all")
	 * @return the created output directory path
	 */
	Path createOutputDirectory(String collectionType, String repository, String state);

	/**
	 * Clean (delete and recreate) an output directory.
	 * @param outputDir the directory to clean
	 */
	void cleanOutputDirectory(Path outputDir);

	/**
	 * Save a batch of data to a file.
	 * @param outputDir the output directory
	 * @param batchIndex the batch number
	 * @param batchData the complete batch data including metadata
	 * @param collectionType type of collection for filename
	 * @param dryRun if true, don't actually write the file
	 * @return the filename that was (or would be) created
	 */
	String saveBatch(Path outputDir, int batchIndex, Map<String, Object> batchData, String collectionType,
			boolean dryRun);

}
