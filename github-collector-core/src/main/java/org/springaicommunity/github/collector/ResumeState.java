package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Internal record for resume state management.
 */
public record ResumeState(String cursor, int batchNumber, int processedIssues, String timestamp,
		List<String> completedBatches) {
}