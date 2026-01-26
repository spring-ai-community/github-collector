package org.springaicommunity.github.collector;

/**
 * Metadata about a completed collection run.
 */
public record CollectionMetadata(String timestamp, String repository, int totalIssues, int processedIssues,
		int batchSize, boolean zipped) {
}