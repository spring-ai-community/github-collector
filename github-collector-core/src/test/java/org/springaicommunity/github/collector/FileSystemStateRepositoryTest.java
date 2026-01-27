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
		objectMapper = new ObjectMapper();
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
			ObjectMapper mapper = new ObjectMapper();
			FileSystemStateRepository repo = new FileSystemStateRepository(mapper);

			// Verify it works by saving a batch
			Path outputDir = repo.createOutputDirectory("issues", "owner/repo", "all");
			Map<String, Object> batchData = Map.of("issues", List.of());

			assertThatCode(() -> repo.saveBatch(outputDir, 1, batchData, "issues", false)).doesNotThrowAnyException();
		}

	}

}
