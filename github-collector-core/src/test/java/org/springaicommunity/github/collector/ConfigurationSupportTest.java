package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for configuration support classes. Uses plain JUnit for all tests.
 */
@DisplayName("ConfigurationSupport Tests")
class ConfigurationSupportTest {

	@Nested
	@DisplayName("CollectionProperties Tests")
	class CollectionPropertiesPlainTest {

		private CollectionProperties collectionProperties;

		@BeforeEach
		void setUp() {
			collectionProperties = new CollectionProperties();
		}

		@Test
		@DisplayName("Should have correct default properties")
		void shouldHaveCorrectDefaultProperties() {
			assertThat(collectionProperties.getDefaultRepository()).isEqualTo("spring-projects/spring-ai");
			assertThat(collectionProperties.getBatchSize()).isEqualTo(100);
			assertThat(collectionProperties.getMaxBatchSizeBytes()).isEqualTo(1048576); // 1MB
			assertThat(collectionProperties.getMaxRetries()).isEqualTo(3);
			assertThat(collectionProperties.getDefaultState()).isEqualTo("closed");
			assertThat(collectionProperties.getDefaultLabelMode()).isEqualTo("any");
		}

		@Test
		@DisplayName("Should have working getters and setters")
		void shouldHaveWorkingGettersAndSetters() {
			collectionProperties.setDefaultRepository("test/repo");
			assertThat(collectionProperties.getDefaultRepository()).isEqualTo("test/repo");

			collectionProperties.setBatchSize(50);
			assertThat(collectionProperties.getBatchSize()).isEqualTo(50);

			collectionProperties.setVerbose(true);
			assertThat(collectionProperties.isVerbose()).isTrue();

			collectionProperties.setDebug(true);
			assertThat(collectionProperties.isDebug()).isTrue();
		}

		@Test
		@DisplayName("Should handle list properties correctly")
		void shouldHandleListPropertiesCorrectly() {
			assertThat(collectionProperties.getDefaultLabels()).isNotNull().isEmpty();

			// Test list modification
			collectionProperties.getDefaultLabels().add("bug");
			collectionProperties.getDefaultLabels().add("enhancement");

			assertThat(collectionProperties.getDefaultLabels()).containsExactly("bug", "enhancement");
		}

		@Test
		@DisplayName("Should validate property constraints")
		void shouldValidatePropertyConstraints() {
			// Test that reasonable defaults are set
			assertThat(collectionProperties.getBatchSize()).isGreaterThan(0);
			assertThat(collectionProperties.getMaxBatchSizeBytes()).isGreaterThan(0);
			assertThat(collectionProperties.getMaxRetries()).isGreaterThanOrEqualTo(0);
			assertThat(collectionProperties.getRateLimit()).isGreaterThan(0);
			assertThat(collectionProperties.getLargeIssueThreshold()).isGreaterThan(0);
			assertThat(collectionProperties.getSizeThreshold()).isGreaterThan(0);
		}

		@Test
		@DisplayName("Should have valid default file paths")
		void shouldHaveValidDefaultFilePaths() {
			assertThat(collectionProperties.getBaseOutputDir()).isNotBlank();
			assertThat(collectionProperties.getResumeFile()).isNotBlank();

			// Ensure paths don't contain dangerous characters
			assertThat(collectionProperties.getBaseOutputDir()).doesNotContain("..");
			assertThat(collectionProperties.getResumeFile()).doesNotContain("..");
		}

		@Test
		@DisplayName("Should support valid issue states and label modes")
		void shouldSupportValidIssueStatesAndLabelModes() {
			String[] validStates = { "open", "closed", "all" };
			String[] validLabelModes = { "any", "all" };

			assertThat(collectionProperties.getDefaultState()).isIn(validStates);
			assertThat(collectionProperties.getDefaultLabelMode()).isIn(validLabelModes);
		}

	}

	@Nested
	@DisplayName("GitHubCollectorBuilder Tests")
	class GitHubCollectorBuilderTest {

		@Test
		@DisplayName("Should create builder instance")
		void shouldCreateBuilderInstance() {
			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create();
			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should accept custom properties")
		void shouldAcceptCustomProperties() {
			CollectionProperties props = new CollectionProperties();
			props.setBatchSize(50);

			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().properties(props);

			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should accept custom ObjectMapper")
		void shouldAcceptCustomObjectMapper() {
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new JavaTimeModule());

			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().objectMapper(mapper);

			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should throw when token not set")
		void shouldThrowWhenTokenNotSet() {
			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create();

			assertThatThrownBy(builder::buildIssueCollector).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("token is required");
		}

		@Test
		@DisplayName("Should throw when PR collector built without token")
		void shouldThrowWhenPRCollectorBuiltWithoutToken() {
			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create();

			assertThatThrownBy(builder::buildPRCollector).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("token is required");
		}

		@Test
		@DisplayName("Should accept token directly")
		void shouldAcceptTokenDirectly() {
			GitHubCollectorBuilder builder = GitHubCollectorBuilder.create().token("test-token");

			// Should not throw - token is set
			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should build issue collector with token")
		void shouldBuildIssueCollectorWithToken() {
			IssueCollectionService collector = GitHubCollectorBuilder.create()
				.token("test-token")
				.buildIssueCollector();

			assertThat(collector).isNotNull();
		}

		@Test
		@DisplayName("Should build PR collector with token")
		void shouldBuildPRCollectorWithToken() {
			PRCollectionService collector = GitHubCollectorBuilder.create().token("test-token").buildPRCollector();

			assertThat(collector).isNotNull();
		}

		@Test
		@DisplayName("Should build REST service with token")
		void shouldBuildRestServiceWithToken() {
			RestService restService = GitHubCollectorBuilder.create().token("test-token").buildRestService();

			assertThat(restService).isNotNull();
		}

		@Test
		@DisplayName("Should build GraphQL service with token")
		void shouldBuildGraphQLServiceWithToken() {
			GraphQLService graphQLService = GitHubCollectorBuilder.create().token("test-token").buildGraphQLService();

			assertThat(graphQLService).isNotNull();
		}

	}

}
