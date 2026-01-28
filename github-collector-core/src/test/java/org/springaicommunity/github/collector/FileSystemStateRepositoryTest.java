package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FileSystemStateRepository}.
 *
 * Tests file I/O operations: directory creation, cleaning, and batch saving.
 */
@DisplayName("FileSystemStateRepository Tests")
class FileSystemStateRepositoryTest {

	@TempDir
	Path tempDir;

	private ObjectMapper objectMapper;

	private FileSystemStateRepository repository;

	@BeforeEach
	void setUp() {
		objectMapper = ObjectMapperFactory.create();
		repository = new FileSystemStateRepository(objectMapper);

		// Change to temp directory for all tests
		System.setProperty("user.dir", tempDir.toString());
	}

	@Nested
	@DisplayName("Directory Creation Tests")
	class DirectoryCreationTest {

		@Test
		@DisplayName("Should create output directory structure")
		void shouldCreateOutputDirectoryStructure() {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "closed");

			assertThat(outputDir).exists();
			assertThat(outputDir.toString()).contains("issues");
			assertThat(outputDir.toString()).contains("raw");
			assertThat(outputDir.toString()).contains("closed");
			assertThat(outputDir.toString()).contains("owner");
			assertThat(outputDir.toString()).contains("repo");
		}

		@Test
		@DisplayName("Should handle different states")
		void shouldHandleDifferentStates() {
			Path openDir = repository.createOutputDirectory("issues", "owner/repo", "open");
			Path closedDir = repository.createOutputDirectory("issues", "owner/repo", "closed");
			Path allDir = repository.createOutputDirectory("issues", "owner/repo", "all");

			assertThat(openDir).exists();
			assertThat(closedDir).exists();
			assertThat(allDir).exists();
			assertThat(openDir.toString()).contains("open");
			assertThat(closedDir.toString()).contains("closed");
			assertThat(allDir.toString()).contains("all");
		}

		@Test
		@DisplayName("Should handle different collection types")
		void shouldHandleDifferentCollectionTypes() {
			Path issuesDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Path prsDir = repository.createOutputDirectory("prs", "owner/repo", "all");

			assertThat(issuesDir).exists();
			assertThat(prsDir).exists();
			assertThat(issuesDir.toString()).contains("issues");
			assertThat(prsDir.toString()).contains("prs");
		}

		@Test
		@DisplayName("Should handle repository names with special characters")
		void shouldHandleRepositoryNamesWithSpecialCharacters() {
			Path outputDir = repository.createOutputDirectory("issues", "spring-projects/spring-ai", "all");

			assertThat(outputDir).exists();
			assertThat(outputDir.toString()).contains("spring-projects");
			assertThat(outputDir.toString()).contains("spring-ai");
		}

		@Test
		@DisplayName("Should be idempotent - creating same directory twice succeeds")
		void shouldBeIdempotent() {
			Path first = repository.createOutputDirectory("issues", "owner/repo", "all");
			Path second = repository.createOutputDirectory("issues", "owner/repo", "all");

			assertThat(first).isEqualTo(second);
			assertThat(first).exists();
		}

	}

	@Nested
	@DisplayName("Directory Cleaning Tests")
	class DirectoryCleaningTest {

		@Test
		@DisplayName("Should clean existing directory contents")
		void shouldCleanExistingDirectoryContents() throws Exception {
			// Create directory with files
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Files.writeString(outputDir.resolve("test.json"), "test content");
			Files.writeString(outputDir.resolve("test2.json"), "test content 2");

			assertThat(Files.list(outputDir).count()).isEqualTo(2);

			// Clean directory
			repository.cleanOutputDirectory(outputDir);

			// Directory should exist but be empty
			assertThat(outputDir).exists();
			assertThat(Files.list(outputDir).count()).isZero();
		}

		@Test
		@DisplayName("Should handle non-existent directory gracefully")
		void shouldHandleNonExistentDirectoryGracefully() {
			Path nonExistent = tempDir.resolve("non/existent/path");

			assertThatCode(() -> repository.cleanOutputDirectory(nonExistent)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Should clean nested directory structure")
		void shouldCleanNestedDirectoryStructure() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");

			// Create nested structure
			Path nestedDir = outputDir.resolve("nested/deep");
			Files.createDirectories(nestedDir);
			Files.writeString(nestedDir.resolve("file.json"), "content");

			// Clean directory
			repository.cleanOutputDirectory(outputDir);

			// Directory should exist but be empty
			assertThat(outputDir).exists();
			assertThat(Files.list(outputDir).count()).isZero();
		}

		@Test
		@DisplayName("Should recreate directory after cleaning")
		void shouldRecreateDirectoryAfterCleaning() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Files.writeString(outputDir.resolve("test.json"), "content");

			repository.cleanOutputDirectory(outputDir);

			// Directory should exist and be writable
			assertThat(outputDir).exists();
			assertThatCode(() -> Files.writeString(outputDir.resolve("new.json"), "new")).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Batch Saving Tests")
	class BatchSavingTest {

		@Test
		@DisplayName("Should save batch as JSON file")
		void shouldSaveBatchAsJsonFile() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");

			Map<String, Object> batchData = Map.of("issues", List.of(Map.of("id", 1, "title", "Test Issue")),
					"metadata", Map.of("count", 1));

			String filename = repository.saveBatch(outputDir, 1, batchData, "issues", false);

			assertThat(filename).isEqualTo("batch_001_issues.json");

			Path savedFile = outputDir.resolve(filename);
			assertThat(savedFile).exists();

			String content = Files.readString(savedFile);
			assertThat(content).contains("Test Issue");
			assertThat(content).contains("\"id\" : 1");
		}

		@Test
		@DisplayName("Should format batch index with leading zeros")
		void shouldFormatBatchIndexWithLeadingZeros() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of());

			String filename1 = repository.saveBatch(outputDir, 1, batchData, "issues", false);
			String filename5 = repository.saveBatch(outputDir, 5, batchData, "issues", false);
			String filename99 = repository.saveBatch(outputDir, 99, batchData, "issues", false);

			assertThat(filename1).isEqualTo("batch_001_issues.json");
			assertThat(filename5).isEqualTo("batch_005_issues.json");
			assertThat(filename99).isEqualTo("batch_099_issues.json");
		}

		@Test
		@DisplayName("Should skip file creation in dry run mode")
		void shouldSkipFileCreationInDryRunMode() {
			// Use unique repository name to avoid conflicts with other tests
			Path outputDir = repository.createOutputDirectory("issues", "dryrun-owner/dryrun-repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of(Map.of("id", 1)));

			String filename = repository.saveBatch(outputDir, 999, batchData, "issues", true);

			// Filename should still be returned
			assertThat(filename).isEqualTo("batch_999_issues.json");

			// But file should not exist
			Path filePath = outputDir.resolve(filename);
			assertThat(filePath).doesNotExist();
		}

		@Test
		@DisplayName("Should handle empty batch data")
		void shouldHandleEmptyBatchData() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of());

			String filename = repository.saveBatch(outputDir, 0, batchData, "issues", false);

			Path savedFile = outputDir.resolve(filename);
			assertThat(savedFile).exists();

			String content = Files.readString(savedFile);
			JsonNode node = objectMapper.readTree(content);
			assertThat(node.get("issues").size()).isZero();
		}

		@Test
		@DisplayName("Should handle different collection types")
		void shouldHandleDifferentCollectionTypes() throws Exception {
			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("prs", List.of(Map.of("number", 42, "title", "PR Title")));

			String filename = repository.saveBatch(outputDir, 1, batchData, "prs", false);

			assertThat(filename).isEqualTo("batch_001_prs.json");

			String content = Files.readString(outputDir.resolve(filename));
			assertThat(content).contains("PR Title");
		}

		@Test
		@DisplayName("Should handle large batch data")
		void shouldHandleLargeBatchData() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");

			// Create large batch with many items
			List<Map<String, Object>> items = new java.util.ArrayList<>();
			for (int i = 0; i < 100; i++) {
				items.add(Map.of("id", i, "title", "Issue " + i, "body", "x".repeat(1000)));
			}
			Map<String, Object> batchData = Map.of("issues", items);

			String filename = repository.saveBatch(outputDir, 1, batchData, "issues", false);

			Path savedFile = outputDir.resolve(filename);
			assertThat(savedFile).exists();
			assertThat(Files.size(savedFile)).isGreaterThan(100000);
		}

		@Test
		@DisplayName("Should use pretty printing for saved JSON")
		void shouldUsePrettyPrintingForSavedJson() throws Exception {
			Path outputDir = repository.createOutputDirectory("issues", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of(Map.of("id", 1)));

			repository.saveBatch(outputDir, 1, batchData, "issues", false);

			String content = Files.readString(outputDir.resolve("batch_001_issues.json"));

			// Pretty printed JSON has newlines and indentation
			assertThat(content).contains("\n");
			assertThat(content).contains("  ");
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTest {

		@Test
		@DisplayName("Should throw exception for invalid repository format")
		void shouldThrowExceptionForInvalidRepositoryFormat() {
			assertThatThrownBy(() -> repository.createOutputDirectory("issues", "invalid-no-slash", "all"))
				.isInstanceOf(ArrayIndexOutOfBoundsException.class);
		}

		@Test
		@DisplayName("Should throw exception when saving to non-existent directory")
		void shouldThrowExceptionWhenSavingToNonExistentDirectory() {
			Path nonExistent = tempDir.resolve("non/existent");
			Map<String, Object> batchData = Map.of("issues", List.of());

			assertThatThrownBy(() -> repository.saveBatch(nonExistent, 1, batchData, "issues", false))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to save batch");
		}

	}

	@Nested
	@DisplayName("Constructor Tests")
	class ConstructorTest {

		@Test
		@DisplayName("Should accept ObjectMapper in constructor")
		void shouldAcceptObjectMapperInConstructor() {
			ObjectMapper mapper = ObjectMapperFactory.create();
			FileSystemStateRepository repo = new FileSystemStateRepository(mapper);

			// Verify it works by saving a batch
			Path outputDir = repo.createOutputDirectory("issues", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of());

			assertThatCode(() -> repo.saveBatch(outputDir, 1, batchData, "issues", false)).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Single-File Mode Tests")
	class SingleFileModeTest {

		@Test
		@DisplayName("Should accumulate items in single-file mode")
		void shouldAccumulateItemsInSingleFileMode() {
			repository.configureSingleFileMode(true, null);

			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "open");

			// Save multiple batches
			Map<String, Object> batch1 = Map.of("prs", List.of(Map.of("number", 1), Map.of("number", 2)));
			Map<String, Object> batch2 = Map.of("prs", List.of(Map.of("number", 3)));

			String result1 = repository.saveBatch(outputDir, 1, batch1, "prs", false);
			String result2 = repository.saveBatch(outputDir, 2, batch2, "prs", false);

			// In single-file mode, saveBatch returns accumulation message
			assertThat(result1).contains("accumulated");
			assertThat(result2).contains("accumulated");

			// No individual batch files should be created
			assertThat(outputDir.resolve("batch_001_prs.json")).doesNotExist();
			assertThat(outputDir.resolve("batch_002_prs.json")).doesNotExist();
		}

		@Test
		@DisplayName("Should write single file on finalize")
		void shouldWriteSingleFileOnFinalize() throws Exception {
			repository.configureSingleFileMode(true, null);

			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "open");

			// Save multiple batches
			Map<String, Object> batch1 = Map.of("prs", List.of(Map.of("number", 1), Map.of("number", 2)));
			Map<String, Object> batch2 = Map.of("prs", List.of(Map.of("number", 3)));

			repository.saveBatch(outputDir, 1, batch1, "prs", false);
			repository.saveBatch(outputDir, 2, batch2, "prs", false);

			// Finalize to write single file
			String outputFile = repository.finalizeCollection(outputDir, "prs", false);

			assertThat(outputFile).isNotNull();
			assertThat(Path.of(outputFile)).exists();

			// Verify content has all PRs
			String content = Files.readString(Path.of(outputFile));
			assertThat(content).contains("\"number\" : 1");
			assertThat(content).contains("\"number\" : 2");
			assertThat(content).contains("\"number\" : 3");
		}

		@Test
		@DisplayName("Should use custom output file path")
		void shouldUseCustomOutputFilePath() throws Exception {
			Path customOutput = tempDir.resolve("custom_output.json");
			repository.configureSingleFileMode(true, customOutput.toString());

			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "open");

			Map<String, Object> batch = Map.of("prs", List.of(Map.of("number", 42)));
			repository.saveBatch(outputDir, 1, batch, "prs", false);

			String result = repository.finalizeCollection(outputDir, "prs", false);

			assertThat(result).isEqualTo(customOutput.toString());
			assertThat(customOutput).exists();

			String content = Files.readString(customOutput);
			assertThat(content).contains("\"number\" : 42");
		}

		@Test
		@DisplayName("Should return null from finalize when not in single-file mode")
		void shouldReturnNullFromFinalizeWhenNotInSingleFileMode() {
			// Default is not single-file mode
			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "open");

			String result = repository.finalizeCollection(outputDir, "prs", false);

			assertThat(result).isNull();
		}

		@Test
		@DisplayName("Should handle dry run in single-file mode")
		void shouldHandleDryRunInSingleFileMode() throws Exception {
			// Use custom output path that doesn't exist
			Path customOutput = tempDir.resolve("dryrun_output.json");
			repository.configureSingleFileMode(true, customOutput.toString());

			Path outputDir = repository.createOutputDirectory("prs", "dryrun-owner/dryrun-repo", "open");

			Map<String, Object> batch = Map.of("prs", List.of(Map.of("number", 1)));
			repository.saveBatch(outputDir, 1, batch, "prs", false);

			// Finalize with dry-run should not write file
			String result = repository.finalizeCollection(outputDir, "prs", true);

			assertThat(result).isNotNull();
			assertThat(customOutput).doesNotExist();
		}

		@Test
		@DisplayName("Should clear accumulated items after finalize")
		void shouldClearAccumulatedItemsAfterFinalize() throws Exception {
			repository.configureSingleFileMode(true, null);

			Path outputDir = repository.createOutputDirectory("prs", "owner/repo", "open");

			// First collection
			Map<String, Object> batch1 = Map.of("prs", List.of(Map.of("number", 1)));
			repository.saveBatch(outputDir, 1, batch1, "prs", false);
			repository.finalizeCollection(outputDir, "prs", false);

			// Second collection (should start fresh)
			Path outputDir2 = repository.createOutputDirectory("prs", "owner/repo2", "open");
			Map<String, Object> batch2 = Map.of("prs", List.of(Map.of("number", 99)));
			repository.saveBatch(outputDir2, 1, batch2, "prs", false);
			String result = repository.finalizeCollection(outputDir2, "prs", false);

			// Second file should only have PR #99
			String content = Files.readString(Path.of(result));
			assertThat(content).contains("\"number\" : 99");
			assertThat(content).doesNotContain("\"number\" : 1");
		}

	}

}
