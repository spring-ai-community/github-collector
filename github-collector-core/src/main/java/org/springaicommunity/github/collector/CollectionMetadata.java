package org.springaicommunity.github.collector;

/**
 * Metadata about a completed collection run.
 *
 * <p>
 * This record captures summary information about a collection operation, suitable for
 * inclusion in batch files or for audit purposes.
 *
 * @param timestamp ISO-8601 formatted timestamp when the collection was performed
 * @param repository the full repository name in "owner/repo" format
 * @param totalIssues the total number of items matching the collection criteria
 * @param processedIssues the number of items collected in this run
 * @param batchSize the maximum number of items per batch file
 * @param zipped whether the output files were compressed
 */
public record CollectionMetadata(String timestamp, String repository, int totalIssues, int processedIssues,
		int batchSize, boolean zipped) {
}