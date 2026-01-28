package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File system implementation of {@link CollectionStateRepository}.
 *
 * <p>
 * Handles persistence of collection batches to the local file system.
 */
public class FileSystemStateRepository implements CollectionStateRepository {

	private static final Logger logger = LoggerFactory.getLogger(FileSystemStateRepository.class);

	private final ObjectMapper objectMapper;

	// Single-file mode state
	private boolean singleFileMode = false;

	@Nullable
	private String customOutputFile = null;

	private final List<Object> accumulatedItems = new ArrayList<>();

	public FileSystemStateRepository(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void configureSingleFileMode(boolean singleFile, @Nullable String outputFile) {
		this.singleFileMode = singleFile;
		this.customOutputFile = outputFile;
		if (singleFile) {
			this.accumulatedItems.clear();
			logger.info("Single-file mode enabled, output file: {}",
					outputFile != null ? outputFile : "(default based on collection type)");
		}
	}

	@Override
	public Path createOutputDirectory(String collectionType, String repository, String state) {
		String[] repoParts = repository.split("/");
		String owner = repoParts[0];
		String repo = repoParts[1];

		String baseDir = collectionType + "/raw/" + state;
		Path outputDir = Paths.get(baseDir, owner, repo);

		try {
			Files.createDirectories(outputDir);
			logger.info("Created output directory: {}", outputDir);
			return outputDir;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to create output directory: " + outputDir, e);
		}
	}

	@Override
	public void cleanOutputDirectory(Path outputDir) {
		try {
			if (Files.exists(outputDir)) {
				Files.walk(outputDir).sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.delete(path);
					}
					catch (Exception e) {
						logger.warn("Failed to delete: {}", path);
					}
				});

				// Recreate the directory after cleaning
				Files.createDirectories(outputDir);
				logger.info("Cleaned output directory: {}", outputDir);
			}
		}
		catch (Exception e) {
			logger.warn("Failed to clean output directory: {}", e.getMessage());
		}
	}

	@Override
	public String saveBatch(Path outputDir, int batchIndex, Map<String, Object> batchData, String collectionType,
			boolean dryRun) {
		Object items = batchData.get(collectionType);
		int itemCount = 0;
		if (items instanceof java.util.Collection<?> collection) {
			itemCount = collection.size();
		}

		// In single-file mode, accumulate items instead of writing individual batch files
		if (singleFileMode) {
			if (items instanceof java.util.Collection<?> collection) {
				accumulatedItems.addAll(collection);
			}
			logger.info("Single-file mode: accumulated {} {} (total: {})", itemCount, collectionType,
					accumulatedItems.size());
			return "(accumulated for single file)";
		}

		String filename = String.format("batch_%03d_%s.json", batchIndex, collectionType);

		if (dryRun) {
			logger.info("DRY RUN: Would save {} items to {}", itemCount, filename);
			return filename;
		}

		try {
			Path filePath = outputDir.resolve(filename);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), batchData);

			logger.info("Saved batch {} with {} {} to {}", batchIndex, itemCount, collectionType, filename);
			return filename;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to save batch " + batchIndex, e);
		}
	}

	@Override
	public String finalizeCollection(Path outputDir, String collectionType, boolean dryRun) {
		if (!singleFileMode) {
			return null;
		}

		// Determine output file path
		String defaultFilename = "all_" + collectionType + ".json";
		Path outputPath;
		if (customOutputFile != null) {
			outputPath = Paths.get(customOutputFile);
			// If it's a relative path, resolve against current directory
			if (!outputPath.isAbsolute()) {
				outputPath = Paths.get(System.getProperty("user.dir")).resolve(outputPath);
			}
		}
		else {
			outputPath = outputDir.resolve(defaultFilename);
		}

		if (dryRun) {
			logger.info("DRY RUN: Would write {} {} to {}", accumulatedItems.size(), collectionType, outputPath);
			return outputPath.toString();
		}

		try {
			// Ensure parent directory exists
			Files.createDirectories(outputPath.getParent());

			// Create output structure with just the items array
			Map<String, Object> output = new LinkedHashMap<>();
			output.put(collectionType, accumulatedItems);

			objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), output);

			logger.info("Wrote {} {} to single file: {}", accumulatedItems.size(), collectionType, outputPath);
			return outputPath.toString();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to write single file output: " + outputPath, e);
		}
		finally {
			// Clear accumulated items for potential reuse
			accumulatedItems.clear();
		}
	}

}
