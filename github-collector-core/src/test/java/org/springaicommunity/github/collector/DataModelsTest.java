package org.springaicommunity.github.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
		objectMapper = ObjectMapperFactory.create();
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
		@DisplayName("Should create Review record with all fields")
		void shouldCreateReview() {
			// Given
			Author reviewer = new Author("reviewer", "Code Reviewer");
			long id = 12345L;
			String body = "LGTM! Great implementation.";
			String state = "APPROVED";
			LocalDateTime submittedAt = LocalDateTime.of(2023, 1, 15, 14, 30);
			String authorAssociation = "MEMBER";
			String htmlUrl = "https://github.com/owner/repo/pull/1#pullrequestreview-12345";

			// When
			Review review = new Review(id, body, state, submittedAt, reviewer, authorAssociation, htmlUrl);

			// Then
			assertThat(review.id()).isEqualTo(id);
			assertThat(review.body()).isEqualTo(body);
			assertThat(review.state()).isEqualTo(state);
			assertThat(review.submittedAt()).isEqualTo(submittedAt);
			assertThat(review.author()).isEqualTo(reviewer);
			assertThat(review.authorAssociation()).isEqualTo(authorAssociation);
			assertThat(review.htmlUrl()).isEqualTo(htmlUrl);
		}

		@Test
		@DisplayName("Should create IssueEvent record with label")
		void shouldCreateIssueEventWithLabel() {
			// Given
			Author actor = new Author("maintainer", "Project Maintainer");
			Label label = new Label("bug", "d73a49", "Something isn't working");
			long id = 98765L;
			String event = "labeled";
			LocalDateTime createdAt = LocalDateTime.of(2023, 1, 15, 10, 30);

			// When
			IssueEvent issueEvent = new IssueEvent(id, event, actor, label, createdAt);

			// Then
			assertThat(issueEvent.id()).isEqualTo(id);
			assertThat(issueEvent.event()).isEqualTo(event);
			assertThat(issueEvent.actor()).isEqualTo(actor);
			assertThat(issueEvent.label()).isEqualTo(label);
			assertThat(issueEvent.createdAt()).isEqualTo(createdAt);
		}

		@Test
		@DisplayName("Should create IssueEvent record without label for non-label events")
		void shouldCreateIssueEventWithoutLabel() {
			// Given
			Author actor = new Author("maintainer", "Project Maintainer");
			long id = 98766L;
			String event = "closed";
			LocalDateTime createdAt = LocalDateTime.of(2023, 1, 15, 11, 0);

			// When
			IssueEvent issueEvent = new IssueEvent(id, event, actor, null, createdAt);

			// Then
			assertThat(issueEvent.id()).isEqualTo(id);
			assertThat(issueEvent.event()).isEqualTo(event);
			assertThat(issueEvent.actor()).isEqualTo(actor);
			assertThat(issueEvent.label()).isNull();
			assertThat(issueEvent.createdAt()).isEqualTo(createdAt);
		}

		@Test
		@DisplayName("Should create Collaborator record with permissions")
		void shouldCreateCollaboratorWithPermissions() {
			// Given
			String login = "maintainer";
			long id = 12345L;
			String type = "User";
			Collaborator.Permissions permissions = new Collaborator.Permissions(true, true, true, true, true);
			String roleName = "admin";

			// When
			Collaborator collaborator = new Collaborator(login, id, type, permissions, roleName);

			// Then
			assertThat(collaborator.login()).isEqualTo(login);
			assertThat(collaborator.id()).isEqualTo(id);
			assertThat(collaborator.type()).isEqualTo(type);
			assertThat(collaborator.permissions()).isNotNull();
			assertThat(collaborator.permissions().admin()).isTrue();
			assertThat(collaborator.permissions().push()).isTrue();
			assertThat(collaborator.roleName()).isEqualTo(roleName);
		}

		@Test
		@DisplayName("Should create Collaborator record with write permissions only")
		void shouldCreateCollaboratorWithWritePermissions() {
			// Given
			Collaborator.Permissions permissions = new Collaborator.Permissions(false, false, true, true, true);

			// When
			Collaborator collaborator = new Collaborator("contributor", 67890L, "User", permissions, "write");

			// Then
			assertThat(collaborator.permissions().admin()).isFalse();
			assertThat(collaborator.permissions().maintain()).isFalse();
			assertThat(collaborator.permissions().push()).isTrue();
			assertThat(collaborator.permissions().triage()).isTrue();
			assertThat(collaborator.permissions().pull()).isTrue();
			assertThat(collaborator.roleName()).isEqualTo("write");
		}

		@Test
		@DisplayName("Should create PullRequest record with all fields")
		void shouldCreatePullRequest() {
			// Given
			Author author = new Author("prauthor", "PR Author");
			Author reviewer = new Author("reviewer", "Reviewer");
			Label bugLabel = new Label("bug", "d73a49", "Bug fix");
			Comment comment = new Comment(author, "Ready for review", LocalDateTime.now());
			Review review = new Review(1L, "LGTM", "APPROVED", LocalDateTime.now(), reviewer, "MEMBER",
					"https://github.com/owner/repo/pull/42#review");

			int number = 42;
			String title = "Fix critical bug";
			String body = "This PR fixes issue #123";
			String state = "closed";
			LocalDateTime createdAt = LocalDateTime.of(2023, 1, 15, 10, 0);
			LocalDateTime updatedAt = LocalDateTime.of(2023, 1, 16, 14, 0);
			LocalDateTime closedAt = LocalDateTime.of(2023, 1, 16, 14, 0);
			LocalDateTime mergedAt = LocalDateTime.of(2023, 1, 16, 14, 0);
			String url = "https://api.github.com/repos/owner/repo/pulls/42";
			String htmlUrl = "https://github.com/owner/repo/pull/42";
			boolean draft = false;
			boolean merged = true;
			String mergeCommitSha = "abc123def456";
			String headRef = "feature/fix-bug";
			String baseRef = "main";
			int additions = 50;
			int deletions = 10;
			int changedFiles = 3;

			// When
			PullRequest pr = new PullRequest(number, title, body, state, createdAt, updatedAt, closedAt, mergedAt, url,
					htmlUrl, author, List.of(comment), List.of(bugLabel), List.of(review), draft, merged,
					mergeCommitSha, headRef, baseRef, additions, deletions, changedFiles);

			// Then
			assertThat(pr.number()).isEqualTo(number);
			assertThat(pr.title()).isEqualTo(title);
			assertThat(pr.body()).isEqualTo(body);
			assertThat(pr.state()).isEqualTo(state);
			assertThat(pr.createdAt()).isEqualTo(createdAt);
			assertThat(pr.updatedAt()).isEqualTo(updatedAt);
			assertThat(pr.closedAt()).isEqualTo(closedAt);
			assertThat(pr.mergedAt()).isEqualTo(mergedAt);
			assertThat(pr.url()).isEqualTo(url);
			assertThat(pr.htmlUrl()).isEqualTo(htmlUrl);
			assertThat(pr.author()).isEqualTo(author);
			assertThat(pr.comments()).containsExactly(comment);
			assertThat(pr.labels()).containsExactly(bugLabel);
			assertThat(pr.reviews()).containsExactly(review);
			assertThat(pr.draft()).isFalse();
			assertThat(pr.merged()).isTrue();
			assertThat(pr.mergeCommitSha()).isEqualTo(mergeCommitSha);
			assertThat(pr.headRef()).isEqualTo(headRef);
			assertThat(pr.baseRef()).isEqualTo(baseRef);
			assertThat(pr.additions()).isEqualTo(additions);
			assertThat(pr.deletions()).isEqualTo(deletions);
			assertThat(pr.changedFiles()).isEqualTo(changedFiles);
		}

		@Test
		@DisplayName("Should create Release record with all fields")
		void shouldCreateRelease() {
			// Given
			Author author = new Author("releasemanager", "Release Manager");
			long id = 123456L;
			String tagName = "v1.0.0";
			String name = "Spring AI 1.0.0 GA";
			String body = "## What's New\n\n- Feature A\n- Bug fix #123\n\n## Bug Fixes\n\n- Fixed #456";
			boolean draft = false;
			boolean prerelease = false;
			LocalDateTime createdAt = LocalDateTime.of(2023, 6, 15, 10, 0);
			LocalDateTime publishedAt = LocalDateTime.of(2023, 6, 15, 12, 0);
			String htmlUrl = "https://github.com/spring-projects/spring-ai/releases/tag/v1.0.0";

			// When
			Release release = new Release(id, tagName, name, body, draft, prerelease, createdAt, publishedAt, author,
					htmlUrl);

			// Then
			assertThat(release.id()).isEqualTo(id);
			assertThat(release.tagName()).isEqualTo(tagName);
			assertThat(release.name()).isEqualTo(name);
			assertThat(release.body()).isEqualTo(body);
			assertThat(release.draft()).isFalse();
			assertThat(release.prerelease()).isFalse();
			assertThat(release.createdAt()).isEqualTo(createdAt);
			assertThat(release.publishedAt()).isEqualTo(publishedAt);
			assertThat(release.author()).isEqualTo(author);
			assertThat(release.htmlUrl()).isEqualTo(htmlUrl);
		}

		@Test
		@DisplayName("Should create Release record for draft release")
		void shouldCreateDraftRelease() {
			// Given
			Author author = new Author("releasemanager", "Release Manager");

			// When - draft release has no publishedAt
			Release release = new Release(789L, "v2.0.0-SNAPSHOT", "2.0.0 Development", "Work in progress", true, false,
					LocalDateTime.now(), null, author, "https://github.com/owner/repo/releases/tag/v2.0.0-SNAPSHOT");

			// Then
			assertThat(release.draft()).isTrue();
			assertThat(release.publishedAt()).isNull();
		}

		@Test
		@DisplayName("Should create Release record for prerelease")
		void shouldCreatePrereleaseRelease() {
			// Given
			Author author = new Author("releasemanager", "Release Manager");

			// When
			Release release = new Release(456L, "v1.0.0-M1", "Spring AI 1.0.0 Milestone 1", "Milestone release", false,
					true, LocalDateTime.now(), LocalDateTime.now(), author,
					"https://github.com/owner/repo/releases/tag/v1.0.0-M1");

			// Then
			assertThat(release.prerelease()).isTrue();
			assertThat(release.draft()).isFalse();
		}

		@Test
		@DisplayName("Should create Issue record with all fields")
		void shouldCreateIssue() {
			// Given
			Author author = new Author("issueauthor", "Issue Author");
			Author maintainer = new Author("maintainer", "Maintainer");
			Label bugLabel = new Label("bug", "d73a49", "Bug label");
			Comment comment = new Comment(author, "Test comment", LocalDateTime.now());
			IssueEvent labelEvent = new IssueEvent(98765L, "labeled", maintainer, bugLabel,
					LocalDateTime.of(2023, 1, 15, 11, 0));

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
			List<IssueEvent> events = List.of(labelEvent);

			// When
			Issue issue = new Issue(number, title, body, state, createdAt, updatedAt, closedAt, url, author, comments,
					labels, events);

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
			assertThat(issue.events()).containsExactly(labelEvent);
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
		@DisplayName("Should create CollectionRequest using builder with defaults")
		void shouldCreateCollectionRequestUsingBuilderWithDefaults() {
			// When
			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").build();

			// Then
			assertThat(request.repository()).isEqualTo("owner/repo");
			assertThat(request.batchSize()).isEqualTo(100);
			assertThat(request.dryRun()).isFalse();
			assertThat(request.incremental()).isFalse();
			assertThat(request.clean()).isTrue();
			assertThat(request.issueState()).isEqualTo("open");
			assertThat(request.collectionType()).isEqualTo("issues");
			assertThat(request.singleFile()).isFalse();
			assertThat(request.outputFile()).isNull();
		}

		@Test
		@DisplayName("Should create CollectionRequest using builder with all options")
		void shouldCreateCollectionRequestUsingBuilderWithAllOptions() {
			// When
			CollectionRequest request = CollectionRequest.builder()
				.repository("spring-projects/spring-ai")
				.batchSize(50)
				.dryRun(true)
				.incremental(true)
				.collectionType("prs")
				.prState("merged")
				.singleFile(true)
				.outputFile("all_prs.json")
				.verbose(true)
				.build();

			// Then
			assertThat(request.repository()).isEqualTo("spring-projects/spring-ai");
			assertThat(request.batchSize()).isEqualTo(50);
			assertThat(request.dryRun()).isTrue();
			assertThat(request.incremental()).isTrue();
			assertThat(request.collectionType()).isEqualTo("prs");
			assertThat(request.prState()).isEqualTo("merged");
			assertThat(request.singleFile()).isTrue();
			assertThat(request.outputFile()).isEqualTo("all_prs.json");
			assertThat(request.verbose()).isTrue();
		}

		@Test
		@DisplayName("Should create builder from existing request using toBuilder")
		void shouldCreateBuilderFromExistingRequest() {
			// Given
			CollectionRequest original = CollectionRequest.builder()
				.repository("owner/repo")
				.collectionType("prs")
				.prState("open")
				.build();

			// When
			CollectionRequest modified = original.toBuilder().singleFile(true).outputFile("output.json").build();

			// Then - original unchanged
			assertThat(original.singleFile()).isFalse();
			assertThat(original.outputFile()).isNull();

			// And - modified has new values but preserves others
			assertThat(modified.repository()).isEqualTo("owner/repo");
			assertThat(modified.collectionType()).isEqualTo("prs");
			assertThat(modified.prState()).isEqualTo("open");
			assertThat(modified.singleFile()).isTrue();
			assertThat(modified.outputFile()).isEqualTo("output.json");
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
					LocalDateTime.now(), "url", new Author("user", "User"), List.of(), List.of(), List.of());
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
		@DisplayName("Should serialize and deserialize Review record")
		void shouldSerializeDeserializeReview() throws JsonProcessingException {
			// Given
			Author reviewer = new Author("reviewer", "Code Reviewer");
			Review original = new Review(12345L, "LGTM!", "APPROVED", LocalDateTime.of(2023, 1, 15, 14, 30), reviewer,
					"MEMBER", "https://github.com/owner/repo/pull/1#review");

			// When
			String json = objectMapper.writeValueAsString(original);
			Review deserialized = objectMapper.readValue(json, Review.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("APPROVED");
			assertThat(json).contains("MEMBER");
			assertThat(json).contains("reviewer");
		}

		@Test
		@DisplayName("Should serialize and deserialize IssueEvent record with label")
		void shouldSerializeDeserializeIssueEventWithLabel() throws JsonProcessingException {
			// Given
			Author actor = new Author("maintainer", "Project Maintainer");
			Label label = new Label("bug", "d73a49", "Bug fix label");
			IssueEvent original = new IssueEvent(98765L, "labeled", actor, label,
					LocalDateTime.of(2023, 1, 15, 10, 30));

			// When
			String json = objectMapper.writeValueAsString(original);
			IssueEvent deserialized = objectMapper.readValue(json, IssueEvent.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("labeled");
			assertThat(json).contains("maintainer");
			assertThat(json).contains("bug");
		}

		@Test
		@DisplayName("Should serialize and deserialize IssueEvent record without label")
		void shouldSerializeDeserializeIssueEventWithoutLabel() throws JsonProcessingException {
			// Given
			Author actor = new Author("maintainer", "Project Maintainer");
			IssueEvent original = new IssueEvent(98766L, "closed", actor, null, LocalDateTime.of(2023, 1, 15, 11, 0));

			// When
			String json = objectMapper.writeValueAsString(original);
			IssueEvent deserialized = objectMapper.readValue(json, IssueEvent.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("closed");
			assertThat(deserialized.label()).isNull();
		}

		@Test
		@DisplayName("Should serialize and deserialize Collaborator record")
		void shouldSerializeDeserializeCollaborator() throws JsonProcessingException {
			// Given
			Collaborator.Permissions permissions = new Collaborator.Permissions(true, true, true, true, true);
			Collaborator original = new Collaborator("maintainer", 12345L, "User", permissions, "admin");

			// When
			String json = objectMapper.writeValueAsString(original);
			Collaborator deserialized = objectMapper.readValue(json, Collaborator.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("maintainer");
			assertThat(json).contains("admin");
			assertThat(deserialized.permissions().admin()).isTrue();
		}

		@Test
		@DisplayName("Should serialize and deserialize Collaborator.Permissions record")
		void shouldSerializeDeserializePermissions() throws JsonProcessingException {
			// Given
			Collaborator.Permissions original = new Collaborator.Permissions(false, false, true, true, true);

			// When
			String json = objectMapper.writeValueAsString(original);
			Collaborator.Permissions deserialized = objectMapper.readValue(json, Collaborator.Permissions.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(deserialized.admin()).isFalse();
			assertThat(deserialized.push()).isTrue();
		}

		@Test
		@DisplayName("Should serialize and deserialize PullRequest record")
		void shouldSerializeDeserializePullRequest() throws JsonProcessingException {
			// Given
			Author author = new Author("prauthor", "PR Author");
			PullRequest original = new PullRequest(42, "Fix bug", "Description", "merged",
					LocalDateTime.of(2023, 1, 15, 10, 0), LocalDateTime.of(2023, 1, 16, 14, 0),
					LocalDateTime.of(2023, 1, 16, 14, 0), LocalDateTime.of(2023, 1, 16, 14, 0),
					"https://api.github.com/repos/owner/repo/pulls/42", "https://github.com/owner/repo/pull/42", author,
					List.of(), List.of(), List.of(), false, true, "abc123", "feature", "main", 100, 50, 5);

			// When
			String json = objectMapper.writeValueAsString(original);
			PullRequest deserialized = objectMapper.readValue(json, PullRequest.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("Fix bug");
			assertThat(json).contains("merged");
			assertThat(json).contains("abc123");
		}

		@Test
		@DisplayName("Should handle null values in PullRequest record")
		void shouldHandleNullValuesInPullRequest() throws JsonProcessingException {
			// Given - PR that is open (not merged, no closedAt/mergedAt)
			Author author = new Author("prauthor", null);
			PullRequest original = new PullRequest(99, "WIP: New feature", null, "open", LocalDateTime.now(),
					LocalDateTime.now(), null, null, "url", "htmlUrl", author, List.of(), List.of(), List.of(), true,
					false, null, "wip-branch", "main", 0, 0, 0);

			// When
			String json = objectMapper.writeValueAsString(original);
			PullRequest deserialized = objectMapper.readValue(json, PullRequest.class);

			// Then
			assertThat(deserialized.body()).isNull();
			assertThat(deserialized.closedAt()).isNull();
			assertThat(deserialized.mergedAt()).isNull();
			assertThat(deserialized.mergeCommitSha()).isNull();
			assertThat(deserialized.draft()).isTrue();
			assertThat(deserialized.merged()).isFalse();
		}

		@Test
		@DisplayName("Should serialize and deserialize Release record")
		void shouldSerializeDeserializeRelease() throws JsonProcessingException {
			// Given
			Author author = new Author("releasemanager", "Release Manager");
			Release original = new Release(123456L, "v1.0.0", "Spring AI 1.0.0", "Release notes with #123 fix", false,
					false, LocalDateTime.of(2023, 6, 15, 10, 0), LocalDateTime.of(2023, 6, 15, 12, 0), author,
					"https://github.com/owner/repo/releases/tag/v1.0.0");

			// When
			String json = objectMapper.writeValueAsString(original);
			Release deserialized = objectMapper.readValue(json, Release.class);

			// Then
			assertThat(deserialized).isEqualTo(original);
			assertThat(json).contains("v1.0.0");
			assertThat(json).contains("Spring AI 1.0.0");
			assertThat(json).contains("releasemanager");
		}

		@Test
		@DisplayName("Should handle null values in Release record")
		void shouldHandleNullValuesInRelease() throws JsonProcessingException {
			// Given - draft release with minimal fields
			Author author = new Author("user", null);
			Release original = new Release(789L, "v2.0.0-dev", null, null, true, false, LocalDateTime.now(), null,
					author, "https://github.com/owner/repo/releases/tag/v2.0.0-dev");

			// When
			String json = objectMapper.writeValueAsString(original);
			Release deserialized = objectMapper.readValue(json, Release.class);

			// Then
			assertThat(deserialized.name()).isNull();
			assertThat(deserialized.body()).isNull();
			assertThat(deserialized.publishedAt()).isNull();
			assertThat(deserialized.draft()).isTrue();
		}

		@Test
		@DisplayName("Should handle null values in Issue record")
		void shouldHandleNullValuesInIssue() throws JsonProcessingException {
			// Given
			Issue original = new Issue(123, "Test Issue", null, "open", LocalDateTime.now(), LocalDateTime.now(), null,
					"url", new Author("user", null), List.of(), List.of(), List.of());

			// When
			String json = objectMapper.writeValueAsString(original);
			Issue deserialized = objectMapper.readValue(json, Issue.class);

			// Then
			assertThat(deserialized.body()).isNull();
			assertThat(deserialized.closedAt()).isNull();
			assertThat(deserialized.author().name()).isNull();
			assertThat(deserialized.events()).isEmpty();
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