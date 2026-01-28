package org.springaicommunity.github.collector;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for GitHub collection.
 *
 * <p>
 * This class provides configuration options for controlling the behavior of issue and
 * pull request collection. Properties can be set directly via setters or passed to
 * {@link GitHubCollectorBuilder}.
 *
 * <p>
 * Default values are provided for all properties and are suitable for most use cases.
 * Adjust batch sizes and rate limiting for large-scale collection.
 */
public class CollectionProperties {

	/**
	 * Default repository to collect from in "owner/repo" format.
	 */
	private String defaultRepository = "spring-projects/spring-ai";

	/**
	 * Maximum number of items per batch file.
	 */
	private int batchSize = 100;

	/**
	 * Maximum size in bytes for a single batch file (default: 1MB).
	 */
	private int maxBatchSizeBytes = 1048576;

	/**
	 * Maximum number of retry attempts for failed API requests.
	 */
	private int maxRetries = 3;

	/**
	 * Delay in seconds between retry attempts.
	 */
	private int retryDelay = 5;

	/**
	 * GitHub API rate limit (requests per hour).
	 */
	private int rateLimit = 5000;

	/**
	 * Number of comments above which an issue is considered "large".
	 */
	private int largeIssueThreshold = 50;

	/**
	 * Size threshold in bytes above which an item is considered "large" (default: 100KB).
	 */
	private int sizeThreshold = 102400;

	/**
	 * Base directory for output files.
	 */
	private String baseOutputDir = "issues/raw/closed";

	/**
	 * File path for storing resume state between collection runs.
	 */
	private String resumeFile = ".resume_state.json";

	/**
	 * Enable verbose logging output.
	 */
	private boolean verbose = false;

	/**
	 * Enable debug-level logging output.
	 */
	private boolean debug = false;

	/**
	 * Default issue/PR state filter ("open", "closed", or "all").
	 */
	private String defaultState = "closed";

	/**
	 * Default labels to filter by.
	 */
	private List<String> defaultLabels = new ArrayList<>();

	/**
	 * Default label matching mode ("any" or "all").
	 */
	private String defaultLabelMode = "any";

	/**
	 * Returns the default repository in "owner/repo" format.
	 * @return the default repository
	 */
	public String getDefaultRepository() {
		return defaultRepository;
	}

	/**
	 * Sets the default repository.
	 * @param defaultRepository repository in "owner/repo" format
	 */
	public void setDefaultRepository(String defaultRepository) {
		this.defaultRepository = defaultRepository;
	}

	/**
	 * Returns the maximum number of items per batch file.
	 * @return the batch size
	 */
	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * Sets the maximum number of items per batch file.
	 * @param batchSize the batch size
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	/**
	 * Returns the maximum batch file size in bytes.
	 * @return the maximum batch size in bytes
	 */
	public int getMaxBatchSizeBytes() {
		return maxBatchSizeBytes;
	}

	/**
	 * Sets the maximum batch file size in bytes.
	 * @param maxBatchSizeBytes the maximum batch size in bytes
	 */
	public void setMaxBatchSizeBytes(int maxBatchSizeBytes) {
		this.maxBatchSizeBytes = maxBatchSizeBytes;
	}

	/**
	 * Returns the maximum number of retry attempts.
	 * @return the maximum retries
	 */
	public int getMaxRetries() {
		return maxRetries;
	}

	/**
	 * Sets the maximum number of retry attempts for failed requests.
	 * @param maxRetries the maximum retries
	 */
	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	/**
	 * Returns the delay in seconds between retry attempts.
	 * @return the retry delay in seconds
	 */
	public int getRetryDelay() {
		return retryDelay;
	}

	/**
	 * Sets the delay in seconds between retry attempts.
	 * @param retryDelay the retry delay in seconds
	 */
	public void setRetryDelay(int retryDelay) {
		this.retryDelay = retryDelay;
	}

	/**
	 * Returns the GitHub API rate limit (requests per hour).
	 * @return the rate limit
	 */
	public int getRateLimit() {
		return rateLimit;
	}

	/**
	 * Sets the GitHub API rate limit.
	 * @param rateLimit requests per hour
	 */
	public void setRateLimit(int rateLimit) {
		this.rateLimit = rateLimit;
	}

	/**
	 * Returns the comment count threshold for large issue detection.
	 * @return the large issue threshold
	 */
	public int getLargeIssueThreshold() {
		return largeIssueThreshold;
	}

	/**
	 * Sets the comment count threshold for large issue detection.
	 * @param largeIssueThreshold number of comments
	 */
	public void setLargeIssueThreshold(int largeIssueThreshold) {
		this.largeIssueThreshold = largeIssueThreshold;
	}

	/**
	 * Returns the size threshold in bytes for large item detection.
	 * @return the size threshold in bytes
	 */
	public int getSizeThreshold() {
		return sizeThreshold;
	}

	/**
	 * Sets the size threshold in bytes for large item detection.
	 * @param sizeThreshold size in bytes
	 */
	public void setSizeThreshold(int sizeThreshold) {
		this.sizeThreshold = sizeThreshold;
	}

	/**
	 * Returns the base output directory for collected data.
	 * @return the base output directory
	 */
	public String getBaseOutputDir() {
		return baseOutputDir;
	}

	/**
	 * Sets the base output directory for collected data.
	 * @param baseOutputDir the output directory path
	 */
	public void setBaseOutputDir(String baseOutputDir) {
		this.baseOutputDir = baseOutputDir;
	}

	/**
	 * Returns the resume state file path.
	 * @return the resume file path
	 */
	public String getResumeFile() {
		return resumeFile;
	}

	/**
	 * Sets the resume state file path.
	 * @param resumeFile the resume file path
	 */
	public void setResumeFile(String resumeFile) {
		this.resumeFile = resumeFile;
	}

	/**
	 * Returns whether verbose logging is enabled.
	 * @return true if verbose logging is enabled
	 */
	public boolean isVerbose() {
		return verbose;
	}

	/**
	 * Enables or disables verbose logging.
	 * @param verbose true to enable verbose logging
	 */
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Returns whether debug logging is enabled.
	 * @return true if debug logging is enabled
	 */
	public boolean isDebug() {
		return debug;
	}

	/**
	 * Enables or disables debug logging.
	 * @param debug true to enable debug logging
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * Returns the default issue/PR state filter.
	 * @return the default state ("open", "closed", or "all")
	 */
	public String getDefaultState() {
		return defaultState;
	}

	/**
	 * Sets the default issue/PR state filter.
	 * @param defaultState "open", "closed", or "all"
	 */
	public void setDefaultState(String defaultState) {
		this.defaultState = defaultState;
	}

	/**
	 * Returns the default labels to filter by.
	 * @return list of label names
	 */
	public List<String> getDefaultLabels() {
		return defaultLabels;
	}

	/**
	 * Sets the default labels to filter by.
	 * @param defaultLabels list of label names
	 */
	public void setDefaultLabels(List<String> defaultLabels) {
		this.defaultLabels = defaultLabels;
	}

	/**
	 * Returns the default label matching mode.
	 * @return "any" or "all"
	 */
	public String getDefaultLabelMode() {
		return defaultLabelMode;
	}

	/**
	 * Sets the default label matching mode.
	 * @param defaultLabelMode "any" to match items with any listed label, "all" to
	 * require all labels
	 */
	public void setDefaultLabelMode(String defaultLabelMode) {
		this.defaultLabelMode = defaultLabelMode;
	}

}