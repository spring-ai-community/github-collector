package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that verifies batch files for duplicates, date-range violations, state
 * mismatches, and batch integrity problems.
 *
 * <p>
 * Operates on raw JSON ({@code readTree}) so it works generically across all collection
 * types without needing deserialization into domain objects.
 */
public class BatchVerificationService {

	private static final Logger logger = LoggerFactory.getLogger(BatchVerificationService.class);

	private static final Pattern BATCH_FILE_PATTERN = Pattern.compile("batch_(\\d+)_(\\w+)\\.json");

	private final ObjectMapper objectMapper;

	public BatchVerificationService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Verify batch files in the given output directory.
	 * @param outputDirectory directory containing batch files
	 * @param collectionType collection type (issues, prs, releases, collaborators)
	 * @param expectedState expected item state (null or "all" to skip state check)
	 * @param createdAfter ISO date lower bound (null to skip)
	 * @param createdBefore ISO date upper bound (null to skip)
	 * @return verification result with all detected issues
	 */
	public VerificationResult verify(Path outputDirectory, String collectionType, @Nullable String expectedState,
			@Nullable String createdAfter, @Nullable String createdBefore) throws IOException {
		List<Path> batchFiles = discoverBatchFiles(outputDirectory, collectionType);

		if (batchFiles.isEmpty()) {
			logger.info("No batch files found in {}", outputDirectory);
			return new VerificationResult(List.of(), List.of(), List.of(), List.of(), 0, 0);
		}

		logger.info("Found {} batch files to verify in {}", batchFiles.size(), outputDirectory);

		// Track items by identifier for duplicate detection
		Map<Long, List<String>> itemLocations = new LinkedHashMap<>();
		List<VerificationResult.DateRangeViolation> dateViolations = new ArrayList<>();
		List<VerificationResult.StateViolation> stateViolations = new ArrayList<>();
		List<VerificationResult.BatchIntegrityIssue> integrityIssues = new ArrayList<>();
		int totalItems = 0;

		String idField = getIdField(collectionType);
		boolean skipDateCheck = "collaborators".equals(collectionType);
		boolean skipStateCheck = expectedState == null || "all".equalsIgnoreCase(expectedState)
				|| "releases".equals(collectionType) || "collaborators".equals(collectionType);

		List<Integer> batchNumbers = new ArrayList<>();

		for (Path batchFile : batchFiles) {
			String fileName = batchFile.getFileName().toString();
			Matcher matcher = BATCH_FILE_PATTERN.matcher(fileName);
			if (matcher.matches()) {
				batchNumbers.add(Integer.parseInt(matcher.group(1)));
			}

			JsonNode root = objectMapper.readTree(batchFile.toFile());
			JsonNode itemsNode = root.get(collectionType);

			if (itemsNode == null || !itemsNode.isArray()) {
				integrityIssues.add(new VerificationResult.BatchIntegrityIssue(fileName,
						"Missing or non-array '" + collectionType + "' field"));
				continue;
			}

			int actualCount = itemsNode.size();
			totalItems += actualCount;

			// Check metadata count
			checkMetadataCount(root, collectionType, actualCount, fileName, integrityIssues);

			// Process each item
			for (JsonNode item : itemsNode) {
				long itemId = getItemId(item, idField);

				// Duplicate tracking
				itemLocations.computeIfAbsent(itemId, k -> new ArrayList<>()).add(fileName);

				// Date range check
				if (!skipDateCheck) {
					checkDateRange(item, itemId, fileName, createdAfter, createdBefore, dateViolations);
				}

				// State check
				if (!skipStateCheck) {
					checkState(item, itemId, fileName, expectedState, collectionType, stateViolations);
				}
			}
		}

		// Check sequential batch numbering
		checkBatchNumbering(batchNumbers, integrityIssues);

		// Build duplicate entries (only items appearing in 2+ files)
		List<VerificationResult.DuplicateEntry> duplicates = itemLocations.entrySet()
			.stream()
			.filter(e -> e.getValue().size() > 1)
			.map(e -> new VerificationResult.DuplicateEntry(e.getKey(), List.copyOf(e.getValue())))
			.toList();

		return new VerificationResult(duplicates, dateViolations, stateViolations, integrityIssues, batchFiles.size(),
				totalItems);
	}

	List<Path> discoverBatchFiles(Path outputDirectory, String collectionType) throws IOException {
		List<Path> files = new ArrayList<>();
		if (!Files.isDirectory(outputDirectory)) {
			return files;
		}

		String glob = "batch_*_" + collectionType + ".json";
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDirectory, glob)) {
			for (Path path : stream) {
				files.add(path);
			}
		}

		files.sort(Comparator.comparing(p -> p.getFileName().toString()));
		return files;
	}

	private String getIdField(String collectionType) {
		return switch (collectionType) {
			case "releases", "collaborators" -> "id";
			default -> "number";
		};
	}

	private long getItemId(JsonNode item, String idField) {
		JsonNode idNode = item.get(idField);
		if (idNode != null && idNode.isNumber()) {
			return idNode.asLong();
		}
		return -1;
	}

	private void checkMetadataCount(JsonNode root, String collectionType, int actualCount, String fileName,
			List<VerificationResult.BatchIntegrityIssue> issues) {
		JsonNode metadata = root.get("metadata");
		if (metadata == null) {
			return; // No metadata wrapper — acceptable for some collection types
		}

		// Try item_count first (standard), then type-specific variants
		JsonNode countNode = metadata.get("item_count");
		if (countNode == null) {
			String typeCountField = collectionType.replace("s", "") + "_count";
			if ("collaborators".equals(collectionType)) {
				typeCountField = "collaborator_count";
			}
			else if ("releases".equals(collectionType)) {
				typeCountField = "release_count";
			}
			countNode = metadata.get(typeCountField);
		}

		if (countNode != null && countNode.isNumber()) {
			int declaredCount = countNode.asInt();
			if (declaredCount != actualCount) {
				issues.add(new VerificationResult.BatchIntegrityIssue(fileName,
						"Declared count " + declaredCount + " but actual count is " + actualCount));
			}
		}
	}

	private void checkDateRange(JsonNode item, long itemNumber, String fileName, @Nullable String createdAfter,
			@Nullable String createdBefore, List<VerificationResult.DateRangeViolation> violations) {
		JsonNode createdAtNode = item.get("created_at");
		if (createdAtNode == null || createdAtNode.isNull()) {
			return;
		}

		String createdAt = createdAtNode.asText();
		// Extract just the date portion (YYYY-MM-DD) for comparison
		String dateOnly = createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;

		if (createdAfter != null && dateOnly.compareTo(createdAfter) < 0) {
			violations.add(new VerificationResult.DateRangeViolation(itemNumber, createdAt, fileName,
					"Created before lower bound " + createdAfter));
		}

		if (createdBefore != null && dateOnly.compareTo(createdBefore) >= 0) {
			violations.add(new VerificationResult.DateRangeViolation(itemNumber, createdAt, fileName,
					"Created on or after upper bound " + createdBefore));
		}
	}

	private void checkState(JsonNode item, long itemNumber, String fileName, String expectedState,
			String collectionType, List<VerificationResult.StateViolation> violations) {
		// For PRs with expected state "merged", check the merged field
		if ("prs".equals(collectionType) && "merged".equalsIgnoreCase(expectedState)) {
			JsonNode mergedNode = item.get("merged");
			if (mergedNode != null && !mergedNode.asBoolean()) {
				String actualState = item.has("state") ? item.get("state").asText() : "unknown";
				violations.add(new VerificationResult.StateViolation(itemNumber, expectedState, actualState, fileName));
			}
			return;
		}

		JsonNode stateNode = item.get("state");
		if (stateNode == null || stateNode.isNull()) {
			return;
		}

		String actualState = stateNode.asText();
		if (!actualState.equalsIgnoreCase(expectedState)) {
			violations.add(new VerificationResult.StateViolation(itemNumber, expectedState, actualState, fileName));
		}
	}

	private void checkBatchNumbering(List<Integer> batchNumbers, List<VerificationResult.BatchIntegrityIssue> issues) {
		if (batchNumbers.isEmpty()) {
			return;
		}

		Collections.sort(batchNumbers);
		for (int i = 0; i < batchNumbers.size(); i++) {
			int expected = i + 1;
			int actual = batchNumbers.get(i);
			if (actual != expected) {
				issues.add(new VerificationResult.BatchIntegrityIssue("batch numbering",
						"Expected batch " + expected + " but found batch " + actual + " (non-sequential)"));
				break; // Report only the first gap
			}
		}
	}

}
