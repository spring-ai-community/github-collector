package org.springaicommunity.github.collector;

import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for creating archives from batch files.
 *
 * <p>
 * Abstracts archive creation to enable testability and potential alternative formats.
 */
public interface ArchiveService {

	/**
	 * Create an archive from the specified batch files.
	 * @param outputDir the directory containing the batch files
	 * @param batchFiles list of batch filenames to include
	 * @param archiveName base name for the archive (without extension)
	 * @param dryRun if true, don't actually create the archive
	 */
	void createArchive(Path outputDir, List<String> batchFiles, String archiveName, boolean dryRun);

}
