package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP implementation of {@link ArchiveService}.
 *
 * <p>
 * Creates ZIP archives from batch files.
 */
public class ZipArchiveService implements ArchiveService {

	private static final Logger logger = LoggerFactory.getLogger(ZipArchiveService.class);

	@Override
	public void createArchive(Path outputDir, List<String> batchFiles, String archiveName, boolean dryRun) {
		if (dryRun) {
			logger.info("DRY RUN: Would create ZIP file with {} batch files", batchFiles.size());
			return;
		}

		try {
			String zipFilename = archiveName + ".zip";
			Path zipPath = outputDir.resolve(zipFilename);

			try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
				for (String batchFile : batchFiles) {
					Path filePath = outputDir.resolve(batchFile);
					if (Files.exists(filePath)) {
						ZipEntry entry = new ZipEntry(batchFile);
						zos.putNextEntry(entry);
						Files.copy(filePath, zos);
						zos.closeEntry();
					}
				}
			}

			logger.info("Created ZIP file: {} with {} batch files", zipFilename, batchFiles.size());
		}
		catch (Exception e) {
			logger.error("Failed to create ZIP file", e);
			throw new RuntimeException("Failed to create ZIP file", e);
		}
	}

}
