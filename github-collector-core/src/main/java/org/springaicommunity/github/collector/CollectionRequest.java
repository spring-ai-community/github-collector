package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Configuration parameters for an issue collection request.
 *
 * Enhanced to support dashboard and UI use cases that require limited, sorted, and
 * filtered issue collections.
 */
public record CollectionRequest(
		// Original parameters
		String repository, int batchSize, boolean dryRun, boolean incremental, boolean zip, boolean clean,
		boolean resume, String issueState, List<String> labelFilters, String labelMode,

		// Phase 1: Essential dashboard parameters
		Integer maxIssues, // null = unlimited (backward compatible)
		String sortBy, // "updated" | "created" | "comments" | "reactions"
		String sortOrder, // "desc" | "asc"

		// Phase 2: PR collection parameters
		String collectionType, // "issues" | "prs"
		Integer prNumber, // specific PR number (when type=prs), null = all
		String prState, // "open" | "closed" | "merged" | "all" (when type=prs)

		// Phase 3: Logging parameters
		boolean verbose // enable verbose logging
) {

	/**
	 * Backward-compatible constructor for existing code.
	 */
	public CollectionRequest(String repository, int batchSize, boolean dryRun, boolean incremental, boolean zip,
			boolean clean, boolean resume, String issueState, List<String> labelFilters, String labelMode) {
		this(repository, batchSize, dryRun, incremental, zip, clean, resume, issueState, labelFilters, labelMode, null, // maxIssues:
																														// unlimited
																														// (backward
																														// compatible)
				"updated", // sortBy: default to updated (GitHub default)
				"desc", // sortOrder: most recent first
				"issues", // collectionType: default to issues (backward compatible)
				null, // prNumber: null = all PRs
				"open", // prState: default PR state
				false // verbose: default to false (backward compatible)
		);
	}

	/**
	 * Dashboard-optimized constructor for UI applications.
	 */
	public static CollectionRequest forDashboard(String repository, String issueState, int maxIssues, String sortBy) {
		return new CollectionRequest(repository, Math.min(maxIssues, 100), // batch size
																			// <=
																			// maxIssues
				false, // dryRun
				false, // incremental
				false, // zip
				true, // clean
				false, // resume
				issueState, List.of(), // labelFilters
				"any", // labelMode
				maxIssues, sortBy, "desc", // most recent first
				"issues", // collectionType: dashboard typically for issues
				null, // prNumber: null = all
				"open", // prState: default
				false // verbose: default to false
		);
	}

	/**
	 * Validate parameters and provide defaults.
	 */
	public CollectionRequest validated() {
		// Validate sortBy
		String validatedSortBy = switch (sortBy == null ? "updated" : sortBy.toLowerCase()) {
			case "updated", "created", "comments", "reactions" -> sortBy.toLowerCase();
			default -> "updated";
		};

		// Validate sortOrder
		String validatedSortOrder = switch (sortOrder == null ? "desc" : sortOrder.toLowerCase()) {
			case "desc", "asc" -> sortOrder.toLowerCase();
			default -> "desc";
		};

		// Validate maxIssues
		Integer validatedMaxIssues = maxIssues != null && maxIssues > 0 ? maxIssues : null;

		// Validate collection type
		String validatedCollectionType = switch (collectionType == null ? "issues" : collectionType.toLowerCase()) {
			case "issues", "prs" -> collectionType.toLowerCase();
			default -> "issues";
		};

		// Validate PR state
		String validatedPrState = switch (prState == null ? "open" : prState.toLowerCase()) {
			case "open", "closed", "merged", "all" -> prState.toLowerCase();
			default -> "open";
		};

		return new CollectionRequest(repository, batchSize, dryRun, incremental, zip, clean, resume, issueState,
				labelFilters, labelMode, validatedMaxIssues, validatedSortBy, validatedSortOrder,
				validatedCollectionType, prNumber, validatedPrState, verbose // preserve
																				// verbose
																				// setting
		);
	}
}