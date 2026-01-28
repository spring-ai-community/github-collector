package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Results of a collection operation.
 *
 * <p>
 * This record summarizes the outcome of an issue or pull request collection, including
 * counts and file locations.
 *
 * @param totalIssues the total number of items matching the collection criteria
 * @param processedIssues the number of items actually collected and saved
 * @param outputDirectory the directory where collected data was saved
 * @param batchFiles list of batch file names created during collection
 */
public record CollectionResult(int totalIssues, int processedIssues, String outputDirectory, List<String> batchFiles) {
}