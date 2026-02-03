package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CollaboratorsCollectionService.
 */
@DisplayName("CollaboratorsCollectionService Tests")
@ExtendWith(MockitoExtension.class)
class CollaboratorsCollectionServiceTest {

	@Mock
	private GraphQLService mockGraphQLService;

	@Mock
	private RestService mockRestService;

	@Mock
	private CollectionStateRepository mockStateRepository;

	@Mock
	private ArchiveService mockArchiveService;

	private ObjectMapper realObjectMapper;

	private CollectionProperties properties;

	private CollaboratorsCollectionService service;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		realObjectMapper = ObjectMapperFactory.create();
		properties = new CollectionProperties();
		BatchStrategy<Collaborator> batchStrategy = new FixedBatchStrategy<>();

		service = new CollaboratorsCollectionService(mockGraphQLService, mockRestService, realObjectMapper, properties,
				mockStateRepository, mockArchiveService, batchStrategy);
	}

	@Nested
	@DisplayName("Collection Operations Tests")
	class CollectionOperationsTest {

		@Test
		@DisplayName("Should collect collaborators successfully")
		void shouldCollectCollaboratorsSuccessfully() {
			// Given
			Collaborator.Permissions adminPerms = new Collaborator.Permissions(true, true, true, true, true);
			Collaborator.Permissions writePerms = new Collaborator.Permissions(false, false, true, true, true);
			List<Collaborator> collaborators = List.of(new Collaborator("admin", 1L, "User", adminPerms, "admin"),
					new Collaborator("writer", 2L, "User", writePerms, "write"));

			when(mockRestService.getRepositoryCollaborators("owner", "repo")).thenReturn(collaborators);
			when(mockStateRepository.createOutputDirectory(eq("collaborators"), eq("owner/repo"), eq("all")))
				.thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), eq(1), any(), eq("collaborators"), eq(false)))
				.thenReturn("collaborators_batch_001.json");

			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").build();

			// When
			CollectionResult result = service.collectItems(request);

			// Then
			assertThat(result.totalIssues()).isEqualTo(2);
			assertThat(result.processedIssues()).isEqualTo(2);
			assertThat(result.batchFiles()).containsExactly("collaborators_batch_001.json");

			verify(mockRestService).getRepositoryCollaborators("owner", "repo");
			verify(mockStateRepository).saveBatch(any(), eq(1), any(), eq("collaborators"), eq(false));
		}

		@Test
		@DisplayName("Should handle dry run mode")
		void shouldHandleDryRunMode() {
			// Given
			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").dryRun(true).build();

			// When
			CollectionResult result = service.collectItems(request);

			// Then
			assertThat(result.totalIssues()).isEqualTo(0);
			assertThat(result.outputDirectory()).isEqualTo("dry-run");

			verifyNoInteractions(mockRestService);
		}

		@Test
		@DisplayName("Should handle empty collaborators list")
		void shouldHandleEmptyCollaboratorsList() {
			// Given
			when(mockRestService.getRepositoryCollaborators("owner", "repo")).thenReturn(List.of());

			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").build();

			// When
			CollectionResult result = service.collectItems(request);

			// Then
			assertThat(result.totalIssues()).isEqualTo(0);
			assertThat(result.outputDirectory()).isEqualTo("empty");
		}

		@Test
		@DisplayName("Should create ZIP archive when requested")
		void shouldCreateZipArchiveWhenRequested() {
			// Given
			List<Collaborator> collaborators = List.of(new Collaborator("user", 1L, "User", null, null));

			when(mockRestService.getRepositoryCollaborators("owner", "repo")).thenReturn(collaborators);
			when(mockStateRepository.createOutputDirectory(any(), any(), any())).thenReturn(tempDir);
			when(mockStateRepository.saveBatch(any(), anyInt(), any(), any(), anyBoolean()))
				.thenReturn("collaborators_batch_001.json");

			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").zip(true).build();

			// When
			service.collectItems(request);

			// Then
			verify(mockArchiveService).createArchive(eq(tempDir), anyList(), contains("collaborators_owner_repo"),
					eq(false));
		}

		@Test
		@DisplayName("Should handle API errors gracefully")
		void shouldHandleAPIErrorsGracefully() {
			// Given
			when(mockRestService.getRepositoryCollaborators("owner", "repo"))
				.thenThrow(new RuntimeException("API error"));

			CollectionRequest request = CollectionRequest.builder().repository("owner/repo").build();

			// When/Then
			assertThatThrownBy(() -> service.collectItems(request)).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Collaborators collection failed");
		}

	}

	@Nested
	@DisplayName("Service Configuration Tests")
	class ServiceConfigurationTest {

		@Test
		@DisplayName("Should return correct collection type")
		void shouldReturnCorrectCollectionType() {
			// Then
			assertThat(service.getItemTypeName()).isEqualTo("collaborators");
		}

		@Test
		@DisplayName("Should be buildable via GitHubCollectorBuilder")
		void shouldBeBuildableViaBuilder() {
			// Given
			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create()
				.httpClient(mock(GitHubClient.class))
				.stateRepository(mockStateRepository)
				.archiveService(mockArchiveService);

			// When
			CollaboratorsCollectionService builtService = builder.buildCollaboratorsCollector();

			// Then
			assertThat(builtService).isNotNull();
		}

	}

}
