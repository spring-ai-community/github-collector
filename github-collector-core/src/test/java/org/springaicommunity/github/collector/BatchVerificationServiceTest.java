package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BatchVerificationService Tests")
class BatchVerificationServiceTest {

	private ObjectMapper objectMapper;

	private BatchVerificationService service;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		objectMapper = ObjectMapperFactory.create();
		service = new BatchVerificationService(objectMapper);
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

	private Map<String, Object> pr(long number, String state, String createdAt, boolean merged) {
		return Map.of("number", number, "state", state, "created_at", createdAt, "merged", merged, "title",
				"PR #" + number);
	}

	@Nested
	@DisplayName("Duplicate Detection Tests")
	class DuplicateDetectionTest {

		@Test
		@DisplayName("Should detect duplicates across batches")
		void shouldDetectDuplicatesAcrossBatches() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
			writeBatchFile("batch_002_issues.json", batchWithMetadata("issues", 2,
					List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"), issue(3, "OPEN", "2025-01-17T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.duplicates()).hasSize(1);
			assertThat(result.duplicates().get(0).itemNumber()).isEqualTo(2);
			assertThat(result.duplicates().get(0).foundInFiles()).containsExactly("batch_001_issues.json",
					"batch_002_issues.json");
			assertThat(result.passed()).isFalse();
		}

		@Test
		@DisplayName("Should pass when no duplicates exist")
		void shouldPassWhenNoDuplicatesExist() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));
			writeBatchFile("batch_002_issues.json", batchWithMetadata("issues", 2,
					List.of(issue(3, "OPEN", "2025-01-17T00:00:00Z"), issue(4, "OPEN", "2025-01-18T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.duplicates()).isEmpty();
			assertThat(result.passed()).isTrue();
		}

	}

	@Nested
	@DisplayName("Date Range Verification Tests")
	class DateRangeTest {

		@Test
		@DisplayName("Should detect item before createdAfter bound")
		void shouldDetectItemBeforeCreatedAfter() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2024-12-31T23:59:59Z"), issue(2, "OPEN", "2025-01-15T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", "2025-01-01", null);

			assertThat(result.dateRangeViolations()).hasSize(1);
			assertThat(result.dateRangeViolations().get(0).itemNumber()).isEqualTo(1);
			assertThat(result.dateRangeViolations().get(0).reason()).contains("before lower bound");
		}

		@Test
		@DisplayName("Should detect item on or after createdBefore bound")
		void shouldDetectItemAfterCreatedBefore() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-06-01T00:00:00Z"), issue(2, "OPEN", "2025-01-15T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, "2025-06-01");

			assertThat(result.dateRangeViolations()).hasSize(1);
			assertThat(result.dateRangeViolations().get(0).itemNumber()).isEqualTo(1);
			assertThat(result.dateRangeViolations().get(0).reason()).contains("upper bound");
		}

		@Test
		@DisplayName("Should pass when all items are within range")
		void shouldPassWhenAllItemsInRange() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-03-15T00:00:00Z"), issue(2, "OPEN", "2025-04-15T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", "2025-01-01", "2025-06-01");

			assertThat(result.dateRangeViolations()).isEmpty();
			assertThat(result.passed()).isTrue();
		}

		@Test
		@DisplayName("Should skip date check for collaborators")
		void shouldSkipDateCheckForCollaborators() throws IOException {
			// Collaborators don't have created_at, but even if they did, we skip checking
			writeBatchFile("batch_001_collaborators.json", Map.of("metadata",
					Map.of("collaborator_count", 1, "collection_type", "collaborators", "repository", "owner/repo"),
					"collaborators", List.of(Map.of("id", 100, "login", "user1"))));

			VerificationResult result = service.verify(tempDir, "collaborators", null, "2025-01-01", "2025-06-01");

			assertThat(result.dateRangeViolations()).isEmpty();
		}

	}

	@Nested
	@DisplayName("State Verification Tests")
	class StateVerificationTest {

		@Test
		@DisplayName("Should detect wrong state")
		void shouldDetectWrongState() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "CLOSED", "2025-01-16T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "open", null, null);

			assertThat(result.stateViolations()).hasSize(1);
			assertThat(result.stateViolations().get(0).itemNumber()).isEqualTo(2);
			assertThat(result.stateViolations().get(0).expectedState()).isEqualTo("open");
			assertThat(result.stateViolations().get(0).actualState()).isEqualTo("CLOSED");
		}

		@Test
		@DisplayName("Should skip state check when expected is 'all'")
		void shouldSkipStateCheckWhenAll() throws IOException {
			writeBatchFile("batch_001_issues.json", batchWithMetadata("issues", 1,
					List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "CLOSED", "2025-01-16T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.stateViolations()).isEmpty();
		}

	}

	@Nested
	@DisplayName("Batch Integrity Tests")
	class BatchIntegrityTest {

		@Test
		@DisplayName("Should detect item_count mismatch")
		void shouldDetectItemCountMismatch() throws IOException {
			// Write a batch where metadata says 3 items but only 2 exist
			writeBatchFile("batch_001_issues.json", Map.of("metadata",
					Map.of("batch_index", 1, "item_count", 3, "collection_type", "issues", "repository", "owner/repo",
							"state", "open"),
					"issues",
					List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"), issue(2, "OPEN", "2025-01-16T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.integrityIssues()).hasSize(1);
			assertThat(result.integrityIssues().get(0).issue()).contains("Declared count 3")
				.contains("actual count is 2");
		}

		@Test
		@DisplayName("Should detect non-sequential batch numbers")
		void shouldDetectNonSequentialBatchNumbers() throws IOException {
			writeBatchFile("batch_001_issues.json",
					batchWithMetadata("issues", 1, List.of(issue(1, "OPEN", "2025-01-15T00:00:00Z"))));
			writeBatchFile("batch_003_issues.json",
					batchWithMetadata("issues", 3, List.of(issue(2, "OPEN", "2025-01-16T00:00:00Z"))));

			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.integrityIssues())
				.anySatisfy(issue -> assertThat(issue.issue()).contains("non-sequential"));
		}

	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTest {

		@Test
		@DisplayName("Should pass with zero files in empty directory")
		void shouldPassWithEmptyDirectory() throws IOException {
			VerificationResult result = service.verify(tempDir, "issues", "all", null, null);

			assertThat(result.filesScanned()).isZero();
			assertThat(result.totalItems()).isZero();
			assertThat(result.passed()).isTrue();
		}

		@Test
		@DisplayName("Should work for prs collection type")
		void shouldWorkForPrsCollectionType() throws IOException {
			writeBatchFile("batch_001_prs.json", batchWithMetadata("prs", 1, List
				.of(pr(100, "CLOSED", "2025-01-15T00:00:00Z", true), pr(101, "OPEN", "2025-01-16T00:00:00Z", false))));

			VerificationResult result = service.verify(tempDir, "prs", "merged", null, null);

			// PR 101 is not merged
			assertThat(result.stateViolations()).hasSize(1);
			assertThat(result.stateViolations().get(0).itemNumber()).isEqualTo(101);
		}

		@Test
		@DisplayName("Should handle files without metadata wrapper")
		void shouldHandleFilesWithoutMetadata() throws IOException {
			// Some collection types may produce files without metadata
			writeBatchFile("batch_001_releases.json", Map.of("releases",
					List.of(Map.of("id", 1, "tag_name", "v1.0"), Map.of("id", 2, "tag_name", "v2.0"))));

			VerificationResult result = service.verify(tempDir, "releases", null, null, null);

			assertThat(result.filesScanned()).isEqualTo(1);
			assertThat(result.totalItems()).isEqualTo(2);
			assertThat(result.passed()).isTrue();
		}

	}

}
