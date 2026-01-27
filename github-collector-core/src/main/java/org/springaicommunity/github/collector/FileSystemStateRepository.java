package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
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

	public FileSystemStateRepository(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
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
		String filename = String.format("batch_%03d_%s.json", batchIndex, collectionType);

		if (dryRun) {
			int itemCount = 0;
			Object items = batchData.get(collectionType);
			if (items instanceof java.util.Collection<?> collection) {
				itemCount = collection.size();
			}
			logger.info("DRY RUN: Would save {} items to {}", itemCount, filename);
			return filename;
		}

		try {
			Path filePath = outputDir.resolve(filename);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), batchData);

			int itemCount = 0;
			Object items = batchData.get(collectionType);
			if (items instanceof java.util.Collection<?> collection) {
				itemCount = collection.size();
			}
			logger.info("Saved batch {} with {} {} to {}", batchIndex, itemCount, collectionType, filename);
			return filename;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to save batch " + batchIndex, e);
		}
	}

}
