package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Internal record for batch processing data.
 */
public record BatchData(int batchNumber, List<Issue> issues, String timestamp) {
}