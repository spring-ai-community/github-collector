package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Service that removes duplicate items from batch files in-place.
 *
 * <p>
 * Uses a keep-first strategy: for each duplicate, the occurrence in the lowest-numbered
 * batch file is kept and all subsequent occurrences are removed. After deduplication,
 * empty batch files are deleted and remaining files are renumbered to restore sequential
 * ordering.
 */
public class BatchDeduplicationService {

	private static final Logger logger = LoggerFactory.getLogger(BatchDeduplicationService.class);

	private static final Pattern BATCH_FILE_PATTERN = Pattern.compile("batch_(\\d+)_(\\w+)\\.json");

	private final ObjectMapper objectMapper;

	public BatchDeduplicationService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Remove duplicates from batch files.
	 * @param outputDirectory directory containing batch files
	 * @param collectionType collection type (issues, prs, releases, collaborators)
	 * @param duplicates list of duplicate entries from verification
	 * @return deduplication result with counts
	 */
	public DeduplicationResult deduplicate(Path outputDirectory, String collectionType,
			List<VerificationResult.DuplicateEntry> duplicates) throws IOException {
		if (duplicates.isEmpty()) {
			return new DeduplicationResult(0, 0, 0, 0);
		}

		String idField = getIdField(collectionType);

		// Build a map: for each duplicate item, which files should have it removed
		// (keep first occurrence, remove from all others)
		Map<Long, Set<String>> removals = new LinkedHashMap<>();
		for (VerificationResult.DuplicateEntry dup : duplicates) {
			List<String> files = dup.foundInFiles();
			// Keep the first file, remove from the rest
			Set<String> removeFrom = new LinkedHashSet<>(files.subList(1, files.size()));
			removals.put(dup.itemNumber(), removeFrom);
		}

		// Discover all batch files
		List<Path> batchFiles = discoverBatchFiles(outputDirectory, collectionType);

		int totalRemoved = 0;
		int filesRewritten = 0;
		List<Path> emptyFiles = new ArrayList<>();

		for (Path batchFile : batchFiles) {
			String fileName = batchFile.getFileName().toString();

			// Collect item IDs to remove from this file
			Set<Long> idsToRemove = new HashSet<>();
			for (Map.Entry<Long, Set<String>> entry : removals.entrySet()) {
				if (entry.getValue().contains(fileName)) {
					idsToRemove.add(entry.getKey());
				}
			}

			if (idsToRemove.isEmpty()) {
				continue;
			}

			// Read, filter, and rewrite
			ObjectNode root = (ObjectNode) objectMapper.readTree(batchFile.toFile());
			JsonNode itemsNode = root.get(collectionType);
			if (itemsNode == null || !itemsNode.isArray()) {
				continue;
			}

			ArrayNode filteredItems = objectMapper.createArrayNode();
			int removedFromFile = 0;
			for (JsonNode item : itemsNode) {
				long itemId = getItemId(item, idField);
				if (idsToRemove.contains(itemId)) {
					removedFromFile++;
				}
				else {
					filteredItems.add(item);
				}
			}

			if (removedFromFile == 0) {
				continue;
			}

			totalRemoved += removedFromFile;
			logger.info("Removing {} duplicates from {}", removedFromFile, fileName);

			if (filteredItems.isEmpty()) {
				// File becomes empty — mark for deletion
				emptyFiles.add(batchFile);
			}
			else {
				// Rewrite file with filtered items and updated count
				root.set(collectionType, filteredItems);
				updateMetadataCount(root, collectionType, filteredItems.size());
				objectMapper.writerWithDefaultPrettyPrinter().writeValue(batchFile.toFile(), root);
				filesRewritten++;
			}
		}

		// Delete empty files
		int filesDeleted = 0;
		for (Path emptyFile : emptyFiles) {
			Files.deleteIfExists(emptyFile);
			logger.info("Deleted empty batch file: {}", emptyFile.getFileName());
			filesDeleted++;
		}

		// Renumber remaining files if any were deleted
		int filesRenumbered = 0;
		if (filesDeleted > 0) {
			filesRenumbered = renumberBatchFiles(outputDirectory, collectionType);
		}

		return new DeduplicationResult(totalRemoved, filesRewritten, filesDeleted, filesRenumbered);
	}

	private List<Path> discoverBatchFiles(Path outputDirectory, String collectionType) throws IOException {
		List<Path> files = new ArrayList<>();
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

	private void updateMetadataCount(ObjectNode root, String collectionType, int newCount) {
		JsonNode metadata = root.get("metadata");
		if (metadata == null || !metadata.isObject()) {
			return;
		}
		ObjectNode metadataObj = (ObjectNode) metadata;

		// Update the appropriate count field
		if (metadataObj.has("item_count")) {
			metadataObj.put("item_count", newCount);
		}
		else if ("releases".equals(collectionType) && metadataObj.has("release_count")) {
			metadataObj.put("release_count", newCount);
		}
		else if ("collaborators".equals(collectionType) && metadataObj.has("collaborator_count")) {
			metadataObj.put("collaborator_count", newCount);
		}
	}

	/**
	 * Renumber batch files to restore sequential numbering (1, 2, 3...). Uses a two-pass
	 * approach via .tmp files to avoid naming collisions.
	 * @return number of files that were renamed
	 */
	int renumberBatchFiles(Path outputDirectory, String collectionType) throws IOException {
		List<Path> remaining = discoverBatchFiles(outputDirectory, collectionType);
		if (remaining.isEmpty()) {
			return 0;
		}

		// First pass: rename all to .tmp
		List<Path> tmpFiles = new ArrayList<>();
		for (Path file : remaining) {
			Path tmpPath = file.resolveSibling(file.getFileName().toString() + ".tmp");
			Files.move(file, tmpPath);
			tmpFiles.add(tmpPath);
		}

		// Second pass: rename from .tmp to sequential names
		int renamed = 0;
		for (int i = 0; i < tmpFiles.size(); i++) {
			String newName = String.format("batch_%03d_%s.json", i + 1, collectionType);
			Path newPath = outputDirectory.resolve(newName);
			Path tmpPath = tmpFiles.get(i);

			String oldName = tmpPath.getFileName().toString().replace(".tmp", "");
			Files.move(tmpPath, newPath);

			if (!newName.equals(oldName)) {
				logger.info("Renumbered {} -> {}", oldName, newName);
				renamed++;
			}
		}

		return renamed;
	}

}
