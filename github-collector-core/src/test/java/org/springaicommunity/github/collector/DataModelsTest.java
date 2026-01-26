package org.springaicommunity.github.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for all DataModels record definitions. Tests record creation, field
 * access, JSON serialization/deserialization, and validation.
 */
@DisplayName("DataModels Tests")
class DataModelsTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
	}

	@Nested
	@DisplayName("Core GitHub Data Structures")
	class CoreDataStructuresTest {

		@Test
		@DisplayName("Should create Author record with login and name")
		void shouldCreateAuthor() {
			// Given
			String login = "testuser";
			String name = "Test User";

			// When
			Author author = new Author(login, name);

			// Then
			assertThat(author.login()).isEqualTo(login);
			assertThat(author.name()).isEqualTo(name);
		}

		@Test
		@DisplayName("Should create Label record with styling information")
		void shouldCreateLabel() {
			// Given
			String name = "bug";
			String color = "d73a49";
			String description = "Something isn't working";

			// When
			Label label = new Label(name, color, description);

			// Then
			assertThat(label.name()).isEqualTo(name);
			assertThat(label.color()).isEqualTo(color);
			assertThat(label.description()).isEqualTo(description);
		}

		@Test
		@DisplayName("Should create Comment record with author and metadata")
		void shouldCreateComment() {
			// Given
			Author author = new Author("commenter", "Commenter User");
			String body = "This is a test comment";
			LocalDateTime createdAt = LocalDateTime.of(2023, 1, 15, 11, 0);

			// When
			Comment comment = new Comment(author, body, createdAt);

			// Then
			assertThat(comment.author()).isEqualTo(author);
			assertThat(comment.body()).isEqualTo(body);
			assertThat(comment.createdAt()).isEqualTo(createdAt);
		}

		@Test
		@DisplayName("Should create Issue record with all fields")
		void shouldCreateIssue() {
			// Given
			Author author = new Author("issueauthor", "Issue Author");
			Label bugLabel = new Label("bug", "d73a49", "Bug label");
			Comment comment = new Comment(author, "Test comment", LocalDateTime.now());

			int number = 123;
			String title = "Test Issue";
			String body = "Issue body";
			String state = "closed";
			LocalDateTime createdAt = LocalDateTime.of(2023, 1, 15, 10, 30);
			LocalDateTime updatedAt = LocalDateTime.of(2023, 1, 16, 14, 45);
			LocalDateTime closedAt = LocalDateTime.of(2023, 1, 16, 14, 45);
			String url = "https://github.com/repo/issues/123";
			List<Comment> comments = List.of(comment);
			List<Label> labels = List.of(bugLabel);

			// When
			Issue issue = new Issue(number, title, body, state, createdAt, updatedAt, closedAt, url, author, comments,
					labels);

			// Then
			assertThat(issue.number()).isEqualTo(number);
			assertThat(issue.title()).isEqualTo(title);
			assertThat(issue.body()).isEqualTo(body);
			assertThat(issue.state()).isEqualTo(state);
			assertThat(issue.createdAt()).isEqualTo(createdAt);
			assertThat(issue.updatedAt()).isEqualTo(updatedAt);
			assertThat(issue.closedAt()).isEqualTo(closedAt);
			assertThat(issue.url()).isEqualTo(url);
			assertThat(issue.author()).isEqualTo(author);
			assertThat(issue.comments()).containsExactly(comment);
			assertThat(issue.labels()).containsExactly(bugLabel);
		}

	}

	@Nested
	@DisplayName("Collection Configuration & Metadata")
	class CollectionConfigurationTest {

		@Test
		@DisplayName("Should create CollectionMetadata record")
		void shouldCreateCollectionMetadata() {
			// Given
			String timestamp = "2023-01-15T10:30:00";
			String repository = "spring-projects/spring-ai";
			int totalIssues = 100;
			int processedIssues = 95;
			int batchSize = 50;
			boolean zipped = true;

			// When
			CollectionMetadata metadata = new CollectionMetadata(timestamp, repository, totalIssues, processedIssues,
					batchSize, zipped);

			// Then
			assertThat(metadata.timestamp()).isEqualTo(timestamp);
			assertThat(metadata.repository()).isEqualTo(repository);
			assertThat(metadata.totalIssues()).isEqualTo(totalIssues);
			assertThat(metadata.processedIssues()).isEqualTo(processedIssues);
			assertThat(metadata.batchSize()).isEqualTo(batchSize);
			assertThat(metadata.zipped()).isEqualTo(zipped);
		}

		@Test
		@DisplayName("Should create CollectionRequest with filtering options")
		void shouldCreateCollectionRequest() {
			// Given
			String repository = "spring-projects/spring-ai";
			int batchSize = 100;
			boolean dryRun = true;
			boolean incremental = false;
			boolean zip = true;
			boolean clean = true;
			boolean resume = false;
			String issueState = "closed";
			List<String> labelFilters = Arrays.asList("bug", "enhancement");
			String labelMode = "any";

			// When
			CollectionRequest request = new CollectionRequest(repository, batchSize, dryRun, incremental, zip, clean,
					resume, issueState, labelFilters, labelMode);

			// Then
			assertThat(request.repository()).isEqualTo(repository);
			assertThat(request.batchSize()).isEqualTo(batchSize);
			assertThat(request.dryRun()).isEqualTo(dryRun);
			assertThat(request.incremental()).isEqualTo(incremental);
			assertThat(request.zip()).isEqualTo(zip);
			assertThat(request.clean()).isEqualTo(clean);
			assertThat(request.resume()).isEqualTo(resume);
			assertThat(request.issueState()).isEqualTo(issueState);
			assertThat(request.labelFilters()).containsExactly("bug", "enhancement");
			assertThat(request.labelMode()).isEqualTo(labelMode);
		}

		@Test
		@DisplayName("Should create CollectionResult record")
		void shouldCreateCollectionResult() {
			// Given
			int totalIssues = 200;
			int processedIssues = 175;
			String outputDirectory = "/tmp/issues";
			List<String> batchFiles = Arrays.asList("batch_001.json", "batch_002.json");

			// When
			CollectionResult result = new CollectionResult(totalIssues, processedIssues, outputDirectory, batchFiles);

			// Then
			assertThat(result.totalIssues()).isEqualTo(totalIssues);
			assertThat(result.processedIssues()).isEqualTo(processedIssues);
			assertThat(result.outputDirectory()).isEqualTo(outputDirectory);
			assertThat(result.batchFiles()).containsExactly("batch_001.json", "batch_002.json");
		}

	}

	@Nested
	@DisplayName("Internal Processing Records")
	class InternalProcessingTest {

		@Test
		@DisplayName("Should create BatchData record")
		void shouldCreateBatchData() {
			// Given
			int batchNumber = 5;
			Issue issue = new Issue(123, "Test", "Body", "closed", LocalDateTime.now(), LocalDateTime.now(),
					LocalDateTime.now(), "url", new Author("user", "User"), List.of(), List.of());
			List<Issue> issues = List.of(issue);
			String timestamp = "2023-01-15T10:30:00";

			// When
			BatchData batchData = new BatchData(batchNumber, issues, timestamp);

			// Then
			assertThat(batchData.batchNumber()).isEqualTo(batchNumber);
			assertThat(batchData.issues()).containsExactly(issue);
			assertThat(batchData.timestamp()).isEqualTo(timestamp);
		}

		@Test
		@DisplayName("Should create CollectionStats record")
		void shouldCreateCollectionStats() {
			// Given
			List<String> batchFiles = Arrays.asList("batch_001.json", "batch_002.json", "batch_003.json");
			int processedIssues = 150;

			// When
			CollectionStats stats = new CollectionStats(batchFiles, processedIssues);

			// Then
			assertThat(stats.batchFiles()).containsExactly("batch_001.json", "batch_002.json", "batch_003.json");
			assertThat(stats.processedIssues()).isEqualTo(processedIssues);
		}

		@Test
		@DisplayName("Should create ResumeState record")
		void shouldCreateResumeState() {
			// Given
			String cursor = "Y3Vyc29yOnYyOpHOABCDEF==";
			int batchNumber = 3;
			int processedIssues = 125;
			String timestamp = "2023-01-15T10:30:00";
			List<String> completedBatches = Arrays.asList("batch_001.json", "batch_002.json");

			// When
			ResumeState resumeState = new ResumeState(cursor, batchNumber, processedIssues, timestamp,
					completedBatches);

			// Then
			assertThat(resumeState.cursor()).isEqualTo(cursor);
			assertThat(resumeState.batchNumber()).isEqualTo(batchNumber);
			assertThat(resumeState.processedIssues()).isEqualTo(processedIssues);
			assertThat(resumeState.timestamp()).isEqualTo(timestamp);
			assertThat(resumeState.completedBatches()).containsExactly("batch_001.json", "batch_002.json");
		}

	}

	@Nested
	@DisplayName("JSON Serialization Tests")
	class JsonSerializationTest {

		@Test
		@DisplayName("Should serialize and deserialize Author record")
		void shouldSerializeDeserializeAuthor() throws JsonProcessingException {
			// Given
			Author original = new Author("testuser", "Test User");

			// When
			String json = objectMapper.writeValueAsString(original);
			Author deserialized = objectMapper.readValue(json, Author.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("testuser");
			assertThat(json).contains("Test User");
		}

		@Test
		@DisplayName("Should serialize and deserialize Label record")
		void shouldSerializeDeserializeLabel() throws JsonProcessingException {
			// Given
			Label original = new Label("bug", "d73a49", "Something isn't working");

			// When
			String json = objectMapper.writeValueAsString(original);
			Label deserialized = objectMapper.readValue(json, Label.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("bug");
			assertThat(json).contains("d73a49");
		}

		@Test
		@DisplayName("Should serialize and deserialize CollectionRequest with filtering")
		void shouldSerializeDeserializeCollectionRequest() throws JsonProcessingException {
			// Given
			CollectionRequest original = new CollectionRequest("spring-projects/spring-ai", 100, true, false, true,
					true, false, "closed", Arrays.asList("bug", "enhancement"), "any");

			// When
			String json = objectMapper.writeValueAsString(original);
			CollectionRequest deserialized = objectMapper.readValue(json, CollectionRequest.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("spring-projects/spring-ai");
			assertThat(json).contains("closed");
			assertThat(json).contains("bug");
			assertThat(json).contains("enhancement");
		}

		@Test
		@DisplayName("Should handle null values in Issue record")
		void shouldHandleNullValuesInIssue() throws JsonProcessingException {
			// Given
			Issue original = new Issue(123, "Test Issue", null, "open", LocalDateTime.now(), LocalDateTime.now(), null,
					"url", new Author("user", null), List.of(), List.of());

			// When
			String json = objectMapper.writeValueAsString(original);
			Issue deserialized = objectMapper.readValue(json, Issue.class);

			// Then
			assertThat(deserialized.body()).isNull();
			assertThat(deserialized.closedAt()).isNull();
			assertThat(deserialized.author().name()).isNull();
		}

	}

	@Nested
	@DisplayName("Record Equality and Immutability")
	class RecordEqualityTest {

		@Test
		@DisplayName("Should demonstrate record equality")
		void shouldDemonstrateRecordEquality() {
			// Given
			Author author1 = new Author("user", "User Name");
			Author author2 = new Author("user", "User Name");
			Author author3 = new Author("different", "User Name");

			// Then
			assertThat(author1).isEqualTo(author2);
			assertThat(author1).isNotEqualTo(author3);
			assertThat(author1.hashCode()).isEqualTo(author2.hashCode());
		}

		@Test
		@DisplayName("Should demonstrate record reference behavior")
		void shouldDemonstrateRecordReferenceBehavior() {
			// Given - records store references, not defensive copies
			List<String> mutableLabels = new ArrayList<>(Arrays.asList("bug", "enhancement"));
			CollectionRequest request = new CollectionRequest("repo", 100, false, false, false, true, false, "open",
					mutableLabels, "any");

			// When - modify the original list after record creation
			mutableLabels.clear();

			// Then - record contains the same reference, so it's affected
			// Note: In production, use immutable lists like List.of() to avoid this
			assertThat(request.labelFilters()).isEmpty();

			// Demonstrate immutable list usage
			List<String> immutableLabels = List.of("documentation", "testing");
			CollectionRequest safeRequest = new CollectionRequest("repo", 100, false, false, false, true, false, "open",
					immutableLabels, "any");
			assertThat(safeRequest.labelFilters()).containsExactly("documentation", "testing");
		}

	}

}