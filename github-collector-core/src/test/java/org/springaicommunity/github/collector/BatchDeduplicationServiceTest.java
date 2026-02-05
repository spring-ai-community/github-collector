package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BatchDeduplicationService Tests")
class BatchDeduplicationServiceTest {

	private ObjectMapper objectMapper;

	private BatchDeduplicationService service;

	private BatchVerificationService verificationService;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		objectMapper = ObjectMapperFactory.create();
		service = new BatchDeduplicationService(objectMapper);
		verificationService = new BatchVerificationService(objectMapper);
	}

	private void writeBatchFile(String filename, Object content) throws IOException {
		Path file = tempDir.resolve(filename);
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), content);
	}

	private Map<String, Object> batchWithMetadata(String type, int batchIndex, List<?> items) {
		return Map.of("metadata", Map.of("batch_index", batchIndex, "item_count", items.size(), "collection_type", type,
				"repository", "owner/repo", "state", "open"), type, items);
	}

	private Map<String, Object> issue(long number, String state, String createdAt) {
		return Map.of("number", number, "state", state, "created_at", createdAt, "title", "Issue #" + number);
	}

	@Test
	@DisplayName("Should remove duplicates keeping first occurrence")
	void shouldRemoveDuplicatesKeepingFirst() throws IOException {
		writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
				List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
		writeBatchFile("batch_002_issues.json", batchWithMetadata("issues", 2,
				List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"), issue(3, "OPEN", "2025-01-17T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(2, List.of("batch_001_issues.json", "batch_002_issues.json")));

		DeduplicationResult result = service.deduplicate(tempDir, "issues", duplicates);

		assertThat(result.duplicatesRemoved()).isEqualTo(1);
		assertThat(result.filesRewritten()).isEqualTo(1);
		assertThat(result.filesDeleted()).isZero();

		// batch_001 should still have items 1 and 2
		JsonNode batch1 = objectMapper.readTree(tempDir.resolve("batch_001_issues.json").toFile());
		assertThat(batch1.get("issues").size()).isEqualTo(2);

		// batch_002 should only have item 3
		JsonNode batch2 = objectMapper.readTree(tempDir.resolve("batch_002_issues.json").toFile());
		assertThat(batch2.get("issues").size()).isEqualTo(1);
		assertThat(batch2.get("issues").get(0).get("number").asLong()).isEqualTo(3);
	}

	@Test
	@DisplayName("Should update metadata item_count after deduplication")
	void shouldUpdateMetadataItemCount() throws IOException {
		writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
				List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
		writeBatchFile("batch_002_issues.json", batchWithMetadata("issues", 2,
				List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"), issue(3, "OPEN", "2025-01-17T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(2, List.of("batch_001_issues.json", "batch_002_issues.json")));

		service.deduplicate(tempDir, "issues", duplicates);

		JsonNode batch2 = objectMapper.readTree(tempDir.resolve("batch_002_issues.json").toFile());
		assertThat(batch2.get("metadata").get("item_count").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("Should delete empty batch files")
	void shouldDeleteEmptyBatchFiles() throws IOException {
		writeBatchFile("batch_001_issues.json",
				batchWithMetadata("issues", 1, List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"))));
		// batch_002 only has the duplicate — will become empty
		writeBatchFile("batch_002_issues.json",
				batchWithMetadata("issues", 2, List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"))));
		writeBatchFile("batch_003_issues.json",
				batchWithMetadata("issues", 3, List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(1, List.of("batch_001_issues.json", "batch_002_issues.json")));

		DeduplicationResult result = service.deduplicate(tempDir, "issues", duplicates);

		assertThat(result.filesDeleted()).isEqualTo(1);
		// batch_003 was renumbered to batch_002, so only 2 batch files remain
		assertThat(Files.exists(tempDir.resolve("batch_003_issues.json"))).isFalse();
		// Verify the remaining batch_002 is the former batch_003 (contains issue #2)
		JsonNode batch2 = objectMapper.readTree(tempDir.resolve("batch_002_issues.json").toFile());
		assertThat(batch2.get("issues").get(0).get("number").asLong()).isEqualTo(2);
	}

	@Test
	@DisplayName("Should renumber after deletion to restore sequential numbering")
	void shouldRenumberAfterDeletion() throws IOException {
		writeBatchFile("batch_001_issues.json",
				batchWithMetadata("issues", 1, List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"))));
		writeBatchFile("batch_002_issues.json",
				batchWithMetadata("issues", 2, List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"))));
		writeBatchFile("batch_003_issues.json",
				batchWithMetadata("issues", 3, List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(1, List.of("batch_001_issues.json", "batch_002_issues.json")));

		DeduplicationResult result = service.deduplicate(tempDir, "issues", duplicates);

		assertThat(result.filesRenumbered()).isGreaterThan(0);
		// After renumbering: batch_001 (original 001) and batch_002 (original 003)
		assertThat(Files.exists(tempDir.resolve("batch_001_issues.json"))).isTrue();
		assertThat(Files.exists(tempDir.resolve("batch_002_issues.json"))).isTrue();
		assertThat(Files.exists(tempDir.resolve("batch_003_issues.json"))).isFalse();
	}

	@Test
	@DisplayName("Should handle files without metadata wrapper")
	void shouldHandleFilesWithoutMetadata() throws IOException {
		// Files without metadata wrapper (just collection type array)
		writeBatchFile("batch_001_issues.json", Map.of("issues",
				List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
		writeBatchFile("batch_002_issues.json", Map.of("issues",
				List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"), issue(3, "OPEN", "2025-01-17T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(2, List.of("batch_001_issues.json", "batch_002_issues.json")));

		DeduplicationResult result = service.deduplicate(tempDir, "issues", duplicates);

		assertThat(result.duplicatesRemoved()).isEqualTo(1);
		JsonNode batch2 = objectMapper.readTree(tempDir.resolve("batch_002_issues.json").toFile());
		assertThat(batch2.get("issues").size()).isEqualTo(1);
	}

	@Test
	@DisplayName("Should be idempotent - running twice produces same result")
	void shouldBeIdempotent() throws IOException {
		writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
				List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
		writeBatchFile("batch_002_issues.json", batchWithMetadata("issues", 2,
				List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"), issue(3, "OPEN", "2025-01-17T00:00:00Z"))));

		List<VerificationResult.DuplicateEntry> duplicates = List
			.of(new VerificationResult.DuplicateEntry(2, List.of("batch_001_issues.json", "batch_002_issues.json")));

		// First run
		service.deduplicate(tempDir, "issues", duplicates);

		// Verify clean
		VerificationResult verifyResult = verificationService.verify(tempDir, "issues", "all", null, null);
		assertThat(verifyResult.duplicates()).isEmpty();

		// Second run with same duplicates — should be a no-op since items already removed
		DeduplicationResult secondResult = service.deduplicate(tempDir, "issues", duplicates);
		assertThat(secondResult.duplicatesRemoved()).isZero();
	}

}
