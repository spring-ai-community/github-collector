package org.springaicommunity.github.collector;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for GitHub collection. Use setters to configure or pass to
 * GitHubCollectorBuilder.
 */
public class CollectionProperties {

	// Repository settings
	private String defaultRepository = "spring-projects/spring-ai";

	// Batch configuration
	private int batchSize = 100;

	private int maxBatchSizeBytes = 1048576; // 1MB

	// Rate limiting
	private int maxRetries = 3;

	private int retryDelay = 5;

	private int rateLimit = 5000; // requests per hour

	// Large issue detection
	private int largeIssueThreshold = 50; // comments

	private int sizeThreshold = 102400; // 100KB

	// File paths
	private String baseOutputDir = "issues/raw/closed";

	private String resumeFile = ".resume_state.json";

	// Logging
	private boolean verbose = false;

	private boolean debug = false;

	// Issue filtering defaults
	private String defaultState = "closed";

	private List<String> defaultLabels = new ArrayList<>();

	private String defaultLabelMode = "any";

	// Getters and setters
	public String getDefaultRepository() {
		return defaultRepository;
	}

	public void setDefaultRepository(String defaultRepository) {
		this.defaultRepository = defaultRepository;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public int getMaxBatchSizeBytes() {
		return maxBatchSizeBytes;
	}

	public void setMaxBatchSizeBytes(int maxBatchSizeBytes) {
		this.maxBatchSizeBytes = maxBatchSizeBytes;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public int getRetryDelay() {
		return retryDelay;
	}

	public void setRetryDelay(int retryDelay) {
		this.retryDelay = retryDelay;
	}

	public int getRateLimit() {
		return rateLimit;
	}

	public void setRateLimit(int rateLimit) {
		this.rateLimit = rateLimit;
	}

	public int getLargeIssueThreshold() {
		return largeIssueThreshold;
	}

	public void setLargeIssueThreshold(int largeIssueThreshold) {
		this.largeIssueThreshold = largeIssueThreshold;
	}

	public int getSizeThreshold() {
		return sizeThreshold;
	}

	public void setSizeThreshold(int sizeThreshold) {
		this.sizeThreshold = sizeThreshold;
	}

	public String getBaseOutputDir() {
		return baseOutputDir;
	}

	public void setBaseOutputDir(String baseOutputDir) {
		this.baseOutputDir = baseOutputDir;
	}

	public String getResumeFile() {
		return resumeFile;
	}

	public void setResumeFile(String resumeFile) {
		this.resumeFile = resumeFile;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public String getDefaultState() {
		return defaultState;
	}

	public void setDefaultState(String defaultState) {
		this.defaultState = defaultState;
	}

	public List<String> getDefaultLabels() {
		return defaultLabels;
	}

	public void setDefaultLabels(List<String> defaultLabels) {
		this.defaultLabels = defaultLabels;
	}

	public String getDefaultLabelMode() {
		return defaultLabelMode;
	}

	public void setDefaultLabelMode(String defaultLabelMode) {
		this.defaultLabelMode = defaultLabelMode;
	}

}