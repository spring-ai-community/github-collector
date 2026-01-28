package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

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
		@Nullable Integer maxIssues, // null = unlimited (backward compatible)
		String sortBy, // "updated" | "created" | "comments" | "reactions"
		String sortOrder, // "desc" | "asc"

		// Phase 2: PR collection parameters
		String collectionType, // "issues" | "prs"
		@Nullable Integer prNumber, // specific PR number (when type=prs), null = all
		String prState, // "open" | "closed" | "merged" | "all" (when type=prs)

		// Phase 3: Logging parameters
		boolean verbose, // enable verbose logging

		// Output options
		boolean singleFile, // output all results to a single JSON file
		@Nullable String outputFile // custom output file path
) {

	/**
	 * Backward-compatible constructor for existing code (10-parameter version).
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
				false, // verbose: default to false (backward compatible)
				false, // singleFile: default to batch mode
				null // outputFile: use default path
		);
	}

	/**
	 * Backward-compatible constructor for existing code (17-parameter version,
	 * pre-single-file).
	 */
	public CollectionRequest(String repository, int batchSize, boolean dryRun, boolean incremental, boolean zip,
			boolean clean, boolean resume, String issueState, List<String> labelFilters, String labelMode,
			@Nullable Integer maxIssues, String sortBy, String sortOrder, String collectionType,
			@Nullable Integer prNumber, String prState, boolean verbose) {
		this(repository, batchSize, dryRun, incremental, zip, clean, resume, issueState, labelFilters, labelMode,
				maxIssues, sortBy, sortOrder, collectionType, prNumber, prState, verbose, false, // singleFile:
																									// default
																									// to
																									// batch
																									// mode
				null // outputFile: use default path
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
				false, // verbose: default to false
				false, // singleFile: default to batch mode
				null // outputFile: use default path
		);
	}

	/**
	 * Create a new builder with sensible defaults.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a builder pre-populated with this request's values.
	 */
	public Builder toBuilder() {
		return new Builder().repository(repository)
			.batchSize(batchSize)
			.dryRun(dryRun)
			.incremental(incremental)
			.zip(zip)
			.clean(clean)
			.resume(resume)
			.issueState(issueState)
			.labelFilters(labelFilters)
			.labelMode(labelMode)
			.maxIssues(maxIssues)
			.sortBy(sortBy)
			.sortOrder(sortOrder)
			.collectionType(collectionType)
			.prNumber(prNumber)
			.prState(prState)
			.verbose(verbose)
			.singleFile(singleFile)
			.outputFile(outputFile);
	}

	/**
	 * Builder for creating CollectionRequest instances with a fluent API.
	 */
	public static class Builder {

		private String repository;

		private int batchSize = 100;

		private boolean dryRun = false;

		private boolean incremental = false;

		private boolean zip = false;

		private boolean clean = true;

		private boolean resume = false;

		private String issueState = "open";

		private List<String> labelFilters = List.of();

		private String labelMode = "any";

		private Integer maxIssues = null;

		private String sortBy = "updated";

		private String sortOrder = "desc";

		private String collectionType = "issues";

		private Integer prNumber = null;

		private String prState = "open";

		private boolean verbose = false;

		private boolean singleFile = false;

		private String outputFile = null;

		public Builder repository(String repository) {
			this.repository = repository;
			return this;
		}

		public Builder batchSize(int batchSize) {
			this.batchSize = batchSize;
			return this;
		}

		public Builder dryRun(boolean dryRun) {
			this.dryRun = dryRun;
			return this;
		}

		public Builder incremental(boolean incremental) {
			this.incremental = incremental;
			return this;
		}

		public Builder zip(boolean zip) {
			this.zip = zip;
			return this;
		}

		public Builder clean(boolean clean) {
			this.clean = clean;
			return this;
		}

		public Builder resume(boolean resume) {
			this.resume = resume;
			return this;
		}

		public Builder issueState(String issueState) {
			this.issueState = issueState;
			return this;
		}

		public Builder labelFilters(List<String> labelFilters) {
			this.labelFilters = labelFilters;
			return this;
		}

		public Builder labelMode(String labelMode) {
			this.labelMode = labelMode;
			return this;
		}

		public Builder maxIssues(Integer maxIssues) {
			this.maxIssues = maxIssues;
			return this;
		}

		public Builder sortBy(String sortBy) {
			this.sortBy = sortBy;
			return this;
		}

		public Builder sortOrder(String sortOrder) {
			this.sortOrder = sortOrder;
			return this;
		}

		public Builder collectionType(String collectionType) {
			this.collectionType = collectionType;
			return this;
		}

		public Builder prNumber(Integer prNumber) {
			this.prNumber = prNumber;
			return this;
		}

		public Builder prState(String prState) {
			this.prState = prState;
			return this;
		}

		public Builder verbose(boolean verbose) {
			this.verbose = verbose;
			return this;
		}

		public Builder singleFile(boolean singleFile) {
			this.singleFile = singleFile;
			return this;
		}

		public Builder outputFile(String outputFile) {
			this.outputFile = outputFile;
			return this;
		}

		public CollectionRequest build() {
			return new CollectionRequest(repository, batchSize, dryRun, incremental, zip, clean, resume, issueState,
					labelFilters, labelMode, maxIssues, sortBy, sortOrder, collectionType, prNumber, prState, verbose,
					singleFile, outputFile);
		}

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
				validatedCollectionType, prNumber, validatedPrState, verbose, // preserve
																				// verbose
																				// setting
				singleFile, // preserve singleFile setting
				outputFile // preserve outputFile setting
		);
	}
}