package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Internal record for tracking collection statistics.
 */
public record CollectionStats(List<String> batchFiles, int processedIssues) {
}