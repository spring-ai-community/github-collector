package org.springaicommunity.github.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ZipArchiveService}.
 *
 * Tests ZIP archive creation, dry run mode, and error handling.
 */
@DisplayName("ZipArchiveService Tests")
class ZipArchiveServiceTest {

	@TempDir
	Path tempDir;

	private ZipArchiveService archiveService;

	@BeforeEach
	void setUp() {
		archiveService = new ZipArchiveService();
	}

	@Nested
	@DisplayName("Archive Creation Tests")
	class ArchiveCreationTest {

		@Test
		@DisplayName("Should create ZIP with all batch files")
		void shouldCreateZipWithAllBatchFiles() throws Exception {
			// Create batch files
			Files.writeString(tempDir.resolve("batch_001_issues.json"), "{\"issues\":[]}");
			Files.writeString(tempDir.resolve("batch_002_issues.json"), "{\"issues\":[]}");
			Files.writeString(tempDir.resolve("batch_003_issues.json"), "{\"issues\":[]}");

			List<String> batchFiles = List.of("batch_001_issues.json", "batch_002_issues.json",
					"batch_003_issues.json");

			archiveService.createArchive(tempDir, batchFiles, "issues_archive", false);

			Path zipPath = tempDir.resolve("issues_archive.zip");
			assertThat(zipPath).exists();

			// Verify ZIP contents
			List<String> entriesInZip = getZipEntries(zipPath);
			assertThat(entriesInZip).containsExactlyInAnyOrder("batch_001_issues.json", "batch_002_issues.json",
					"batch_003_issues.json");
		}

		@Test
		@DisplayName("Should create ZIP file with correct filename")
		void shouldCreateZipFileWithCorrectFilename() throws Exception {
			Files.writeString(tempDir.resolve("batch_001_prs.json"), "{}");
			List<String> batchFiles = List.of("batch_001_prs.json");

			archiveService.createArchive(tempDir, batchFiles, "my_custom_archive", false);

			assertThat(tempDir.resolve("my_custom_archive.zip")).exists();
		}

		@Test
		@DisplayName("Should preserve file contents in archive")
		void shouldPreserveFileContentsInArchive() throws Exception {
			String content1 = "{\"issues\":[{\"id\":1,\"title\":\"Issue 1\"}]}";
			String content2 = "{\"issues\":[{\"id\":2,\"title\":\"Issue 2\"}]}";
			Files.writeString(tempDir.resolve("batch_001_issues.json"), content1);
			Files.writeString(tempDir.resolve("batch_002_issues.json"), content2);

			archiveService.createArchive(tempDir, List.of("batch_001_issues.json", "batch_002_issues.json"), "archive",
					false);

			// Extract and verify content
			Path zipPath = tempDir.resolve("archive.zip");
			String extractedContent = extractFileFromZip(zipPath, "batch_001_issues.json");
			assertThat(extractedContent).isEqualTo(content1);
		}

		@Test
		@DisplayName("Should handle single batch file")
		void shouldHandleSingleBatchFile() throws Exception {
			Files.writeString(tempDir.resolve("batch_001_issues.json"), "{}");

			archiveService.createArchive(tempDir, List.of("batch_001_issues.json"), "single", false);

			assertThat(tempDir.resolve("single.zip")).exists();
			assertThat(getZipEntries(tempDir.resolve("single.zip"))).hasSize(1);
		}

		@Test
		@DisplayName("Should handle empty batch file list")
		void shouldHandleEmptyBatchFileList() {
			archiveService.createArchive(tempDir, List.of(), "empty", false);

			// ZIP should be created but empty
			assertThat(tempDir.resolve("empty.zip")).exists();
		}

		@Test
		@DisplayName("Should skip missing files without failing")
		void shouldSkipMissingFilesWithoutFailing() throws Exception {
			// Only create one of the two files
			Files.writeString(tempDir.resolve("batch_001_issues.json"), "{}");

			List<String> batchFiles = List.of("batch_001_issues.json", "batch_002_missing.json");

			archiveService.createArchive(tempDir, batchFiles, "partial", false);

			Path zipPath = tempDir.resolve("partial.zip");
			assertThat(zipPath).exists();

			// Only the existing file should be in the archive
			List<String> entries = getZipEntries(zipPath);
			assertThat(entries).containsExactly("batch_001_issues.json");
		}

		@Test
		@DisplayName("Should handle large files")
		void shouldHandleLargeFiles() throws Exception {
			// Create a large file (~1MB)
			String largeContent = "x".repeat(1_000_000);
			Files.writeString(tempDir.resolve("large_batch.json"), largeContent);

			archiveService.createArchive(tempDir, List.of("large_batch.json"), "large", false);

			Path zipPath = tempDir.resolve("large.zip");
			assertThat(zipPath).exists();
			// ZIP should be smaller than original due to compression
			assertThat(Files.size(zipPath)).isLessThan(1_000_000);
		}

	}

	@Nested
	@DisplayName("Dry Run Tests")
	class DryRunTest {

		@Test
		@DisplayName("Should not create ZIP file in dry run mode")
		void shouldNotCreateZipFileInDryRunMode() throws Exception {
			Files.writeString(tempDir.resolve("batch_001_issues.json"), "{}");

			archiveService.createArchive(tempDir, List.of("batch_001_issues.json"), "dryrun_archive", true);

			assertThat(tempDir.resolve("dryrun_archive.zip")).doesNotExist();
		}

		@Test
		@DisplayName("Should complete without error in dry run mode")
		void shouldCompleteWithoutErrorInDryRunMode() {
			assertThatCode(() -> archiveService.createArchive(tempDir, List.of("nonexistent.json"), "dryrun", true))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Should handle empty file list in dry run mode")
		void shouldHandleEmptyFileListInDryRunMode() {
			assertThatCode(() -> archiveService.createArchive(tempDir, List.of(), "empty_dryrun", true))
				.doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Archive Naming Tests")
	class ArchiveNamingTest {

		@Test
		@DisplayName("Should append .zip extension to archive name")
		void shouldAppendZipExtension() throws Exception {
			Files.writeString(tempDir.resolve("file.json"), "{}");

			archiveService.createArchive(tempDir, List.of("file.json"), "my_archive", false);

			assertThat(tempDir.resolve("my_archive.zip")).exists();
			// Should not create double extension
			assertThat(tempDir.resolve("my_archive.zip.zip")).doesNotExist();
		}

		@Test
		@DisplayName("Should handle archive names with special characters")
		void shouldHandleArchiveNamesWithSpecialCharacters() throws Exception {
			Files.writeString(tempDir.resolve("file.json"), "{}");

			archiveService.createArchive(tempDir, List.of("file.json"), "spring-ai_issues_2024-01-15", false);

			assertThat(tempDir.resolve("spring-ai_issues_2024-01-15.zip")).exists();
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTest {

		@Test
		@DisplayName("Should throw exception for invalid output directory")
		void shouldThrowExceptionForInvalidOutputDirectory() {
			Path invalidDir = tempDir.resolve("non/existent/deeply/nested/dir");
			List<String> batchFiles = List.of("file.json");

			assertThatThrownBy(() -> archiveService.createArchive(invalidDir, batchFiles, "archive", false))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to create ZIP file");
		}

	}

	// Helper methods

	private List<String> getZipEntries(Path zipPath) throws Exception {
		List<String> entries = new ArrayList<>();
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				entries.add(entry.getName());
				zis.closeEntry();
			}
		}
		return entries;
	}

	private String extractFileFromZip(Path zipPath, String filename) throws Exception {
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().equals(filename)) {
					byte[] bytes = zis.readAllBytes();
					return new String(bytes);
				}
				zis.closeEntry();
			}
		}
		return null;
	}

}
