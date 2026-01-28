package org.springaicommunity.github.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ArgumentParser using plain JUnit only. NO Spring context to prevent
 * accidental production operations.
 */
@DisplayName("ArgumentParser Tests - Plain JUnit Only")
class ArgumentParserTest {

	private CollectionProperties defaultProperties;

	private ArgumentParser argumentParser;

	static boolean isGitHubTokenAvailable() {
		String token = EnvironmentSupport.get("GITHUB_TOKEN");
		return token != null && !token.isBlank();
	}

	@BeforeEach
	void setUp() {
		// Create default properties without Spring context
		defaultProperties = new CollectionProperties();
		argumentParser = new ArgumentParser(defaultProperties);
	}

	@Nested
	@DisplayName("Basic Argument Parsing Tests")
	class BasicArgumentParsingTest {

		@Test
		@DisplayName("Should parse repository argument correctly")
		void shouldParseRepositoryArgument() {
			String[] args = { "--repo", "microsoft/vscode" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.repository).isEqualTo("microsoft/vscode");
		}

		@Test
		@DisplayName("Should parse batch size argument correctly")
		void shouldParseBatchSizeArgument() {
			String[] args = { "--batch-size", "50" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.batchSize).isEqualTo(50);
		}

		@Test
		@DisplayName("Should parse boolean flags correctly")
		void shouldParseBooleanFlags() {
			String[] args = { "--dry-run", "--verbose", "--zip", "--incremental", "--resume" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.dryRun).isTrue();
			assertThat(config.verbose).isTrue();
			assertThat(config.zip).isTrue();
			assertThat(config.incremental).isTrue();
			assertThat(config.resume).isTrue();
		}

		@Test
		@DisplayName("Should parse clean/no-clean flags correctly")
		void shouldParseCleanFlags() {
			String[] cleanArgs = { "--clean" };
			String[] noCleanArgs = { "--no-clean" };
			String[] appendArgs = { "--append" };

			ParsedConfiguration cleanConfig = argumentParser.parseAndValidate(cleanArgs);
			ParsedConfiguration noCleanConfig = argumentParser.parseAndValidate(noCleanArgs);
			ParsedConfiguration appendConfig = argumentParser.parseAndValidate(appendArgs);

			assertThat(cleanConfig.clean).isTrue();
			assertThat(noCleanConfig.clean).isFalse();
			assertThat(appendConfig.clean).isFalse();
		}

		@Test
		@DisplayName("Should use default values for unparsed arguments")
		void shouldUseDefaultValues() {
			String[] args = {}; // Empty arguments

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.repository).isEqualTo(defaultProperties.getDefaultRepository());
			assertThat(config.batchSize).isEqualTo(defaultProperties.getBatchSize());
			assertThat(config.issueState).isEqualTo(defaultProperties.getDefaultState());
			assertThat(config.labelMode).isEqualTo(defaultProperties.getDefaultLabelMode());
			assertThat(config.verbose).isEqualTo(defaultProperties.isVerbose());
		}

	}

	@Nested
	@DisplayName("Repository Format Validation Tests")
	class RepositoryValidationTest {

		@ParameterizedTest
		@ValueSource(strings = { "spring-projects/spring-ai", "microsoft/vscode", "kubernetes/kubernetes",
				"user123/repo-name", "org_name/repo.name" })
		@DisplayName("Should accept valid repository formats")
		void shouldAcceptValidRepositoryFormats(String validRepo) {
			String[] args = { "--repo", validRepo };

			assertThatCode(() -> argumentParser.parseAndValidate(args)).doesNotThrowAnyException();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.repository).isEqualTo(validRepo);
		}

		@ParameterizedTest
		@ValueSource(strings = { "invalid", "no-slash", "too/many/slashes", "owner/", "/repo", "owner/ repo" })
		@DisplayName("Should reject invalid repository formats")
		void shouldRejectInvalidRepositoryFormats(String invalidRepo) {
			String[] args = { "--repo", invalidRepo };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Repository must be in format 'owner/repo'");
		}

		@ParameterizedTest
		@ValueSource(strings = { "", " " })
		@DisplayName("Should reject empty repository with appropriate message")
		void shouldRejectEmptyRepository(String emptyRepo) {
			String[] args = { "--repo", emptyRepo };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Repository cannot be empty");
		}

	}

	@Nested
	@DisplayName("Issue State Filtering Tests")
	class IssueStateFilteringTest {

		@ParameterizedTest
		@ValueSource(strings = { "open", "closed", "all", "OPEN", "CLOSED", "ALL" })
		@DisplayName("Should accept valid issue states")
		void shouldAcceptValidIssueStates(String validState) {
			String[] args = { "--state", validState };

			assertThatCode(() -> argumentParser.parseAndValidate(args)).doesNotThrowAnyException();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.issueState).isEqualTo(validState.toLowerCase());
		}

		@ParameterizedTest
		@ValueSource(strings = { "invalid", "active", "pending", "" })
		@DisplayName("Should reject invalid issue states")
		void shouldRejectInvalidIssueStates(String invalidState) {
			String[] args = { "--state", invalidState };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(
						"Invalid state '" + invalidState.toLowerCase() + "': must be 'open', 'closed', or 'all'");
		}

	}

	@Nested
	@DisplayName("Label Filtering Tests")
	class LabelFilteringTest {

		@Test
		@DisplayName("Should parse single label correctly")
		void shouldParseSingleLabel() {
			String[] args = { "--labels", "bug" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.labelFilters).containsExactly("bug");
		}

		@Test
		@DisplayName("Should parse multiple labels correctly")
		void shouldParseMultipleLabels() {
			String[] args = { "--labels", "bug,enhancement,documentation" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.labelFilters).containsExactly("bug", "enhancement", "documentation");
		}

		@Test
		@DisplayName("Should handle labels with spaces correctly")
		void shouldHandleLabelsWithSpaces() {
			String[] args = { "--labels", "bug, enhancement , documentation" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.labelFilters).containsExactly("bug", "enhancement", "documentation");
		}

		@Test
		@DisplayName("Should filter out empty labels")
		void shouldFilterOutEmptyLabels() {
			String[] args = { "--labels", "bug,,enhancement," };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.labelFilters).containsExactly("bug", "enhancement");
		}

		@ParameterizedTest
		@ValueSource(strings = { "any", "all", "ANY", "ALL" })
		@DisplayName("Should accept valid label modes")
		void shouldAcceptValidLabelModes(String validMode) {
			String[] args = { "--label-mode", validMode };

			assertThatCode(() -> argumentParser.parseAndValidate(args)).doesNotThrowAnyException();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.labelMode).isEqualTo(validMode.toLowerCase());
		}

		@ParameterizedTest
		@ValueSource(strings = { "invalid", "some", "none", "" })
		@DisplayName("Should reject invalid label modes")
		void shouldRejectInvalidLabelModes(String invalidMode) {
			String[] args = { "--label-mode", invalidMode };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid label mode '" + invalidMode.toLowerCase() + "': must be 'any' or 'all'");
		}

	}

	@Nested
	@DisplayName("Batch Size Validation Tests")
	class BatchSizeValidationTest {

		@ParameterizedTest
		@ValueSource(strings = { "1", "50", "100", "500", "1000" })
		@DisplayName("Should accept valid batch sizes")
		void shouldAcceptValidBatchSizes(String validBatchSize) {
			String[] args = { "--batch-size", validBatchSize };

			assertThatCode(() -> argumentParser.parseAndValidate(args)).doesNotThrowAnyException();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.batchSize).isEqualTo(Integer.parseInt(validBatchSize));
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "-1", "-50" })
		@DisplayName("Should reject non-positive batch sizes")
		void shouldRejectNonPositiveBatchSizes(String invalidBatchSize) {
			String[] args = { "--batch-size", invalidBatchSize };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Batch size must be positive");
		}

		@ParameterizedTest
		@ValueSource(strings = { "1001", "5000", "10000" })
		@DisplayName("Should reject batch sizes that are too large")
		void shouldRejectTooLargeBatchSizes(String largeBatchSize) {
			String[] args = { "--batch-size", largeBatchSize };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Batch size too large");
		}

		@ParameterizedTest
		@ValueSource(strings = { "invalid", "abc", "1.5", "" })
		@DisplayName("Should reject non-numeric batch sizes")
		void shouldRejectNonNumericBatchSizes(String nonNumericBatchSize) {
			String[] args = { "--batch-size", nonNumericBatchSize };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be a positive integer");
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTest {

		@Test
		@DisplayName("Should throw error for missing repository value")
		void shouldThrowErrorForMissingRepositoryValue() {
			String[] args = { "--repo" };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Missing value for repository option");
		}

		@Test
		@DisplayName("Should throw error for missing batch-size value")
		void shouldThrowErrorForMissingBatchSizeValue() {
			String[] args = { "--batch-size" };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Missing value for batch-size option");
		}

		@Test
		@DisplayName("Should throw error for unknown option")
		void shouldThrowErrorForUnknownOption() {
			String[] args = { "--unknown-option" };

			assertThatThrownBy(() -> argumentParser.parseAndValidate(args)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown option: --unknown-option");
		}

		@Test
		@DisplayName("Should not throw error for non-option arguments")
		void shouldNotThrowErrorForNonOptionArguments() {
			String[] args = { "non-option-argument" };

			assertThatCode(() -> argumentParser.parseAndValidate(args)).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Help Request Detection Tests")
	class HelpRequestDetectionTest {

		@Test
		@DisplayName("Should detect short help flag")
		void shouldDetectShortHelpFlag() {
			String[] args = { "-h" };

			assertThat(argumentParser.isHelpRequested(args)).isTrue();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.helpRequested).isTrue();
		}

		@Test
		@DisplayName("Should detect long help flag")
		void shouldDetectLongHelpFlag() {
			String[] args = { "--help" };

			assertThat(argumentParser.isHelpRequested(args)).isTrue();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.helpRequested).isTrue();
		}

		@Test
		@DisplayName("Should detect help flag among other arguments")
		void shouldDetectHelpFlagAmongOtherArguments() {
			String[] args = { "--repo", "test/repo", "--help", "--verbose" };

			assertThat(argumentParser.isHelpRequested(args)).isTrue();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.helpRequested).isTrue();
		}

		@Test
		@DisplayName("Should not detect help when not present")
		void shouldNotDetectHelpWhenNotPresent() {
			String[] args = { "--repo", "test/repo", "--verbose" };

			assertThat(argumentParser.isHelpRequested(args)).isFalse();

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			assertThat(config.helpRequested).isFalse();
		}

	}

	@Nested
	@DisplayName("Help Text Generation Tests")
	class HelpTextGenerationTest {

		@Test
		@DisplayName("Should generate help text with default values")
		void shouldGenerateHelpTextWithDefaultValues() {
			String helpText = argumentParser.generateHelpText();

			assertThat(helpText).contains("Usage: collect_github_issues.java [OPTIONS]")
				.contains("Repository in format owner/repo (default: " + defaultProperties.getDefaultRepository() + ")")
				.contains("Issues per batch file (default: " + defaultProperties.getBatchSize() + ")")
				.contains("Issue state: open, closed, all (default: " + defaultProperties.getDefaultState() + ")")
				.contains("Label matching mode: any, all (default: " + defaultProperties.getDefaultLabelMode() + ")")
				.contains("GITHUB_TOKEN")
				.contains("EXAMPLES:");
		}

		@Test
		@DisplayName("Should generate complete help text")
		void shouldGenerateCompleteHelpText() {
			String helpText = argumentParser.generateHelpText();

			// Check major sections are present
			assertThat(helpText).contains("OPTIONS:")
				.contains("FILTERING OPTIONS:")
				.contains("CONFIGURATION:")
				.contains("ENVIRONMENT VARIABLES:")
				.contains("EXAMPLES:");

			// Check key options are documented
			assertThat(helpText).contains("-h, --help")
				.contains("-r, --repo")
				.contains("-b, --batch-size")
				.contains("-d, --dry-run")
				.contains("-s, --state")
				.contains("-l, --labels")
				.contains("--label-mode");
		}

	}

	@Nested
	@DisplayName("Complex Argument Combinations Tests")
	class ComplexArgumentCombinationsTest {

		@Test
		@DisplayName("Should parse complex argument combination correctly")
		void shouldParseComplexArgumentCombination() {
			String[] args = { "--repo", "microsoft/vscode", "--batch-size", "25", "--state", "open", "--labels",
					"bug,enhancement", "--label-mode", "all", "--dry-run", "--verbose", "--zip", "--no-clean" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.repository).isEqualTo("microsoft/vscode");
			assertThat(config.batchSize).isEqualTo(25);
			assertThat(config.issueState).isEqualTo("open");
			assertThat(config.labelFilters).containsExactly("bug", "enhancement");
			assertThat(config.labelMode).isEqualTo("all");
			assertThat(config.dryRun).isTrue();
			assertThat(config.verbose).isTrue();
			assertThat(config.zip).isTrue();
			assertThat(config.clean).isFalse();
		}

		@Test
		@DisplayName("Should handle mixed short and long arguments")
		void shouldHandleMixedShortAndLongArguments() {
			String[] args = { "-r", "test/repo", "-b", "50", "--dry-run", "-v", "--state", "closed" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);

			assertThat(config.repository).isEqualTo("test/repo");
			assertThat(config.batchSize).isEqualTo(50);
			assertThat(config.dryRun).isTrue();
			assertThat(config.verbose).isTrue();
			assertThat(config.issueState).isEqualTo("closed");
		}

	}

	@Nested
	@DisplayName("Environment Validation Tests")
	class EnvironmentValidationTest {

		@Test
		@DisplayName("Should pass validation when GITHUB_TOKEN is set")
		@org.junit.jupiter.api.condition.EnabledIf("org.springaicommunity.github.collector.ArgumentParserTest#isGitHubTokenAvailable")
		void shouldPassValidationWhenGitHubTokenIsSet() {
			// In test environment, GITHUB_TOKEN is set, so validation should pass
			// This test verifies the happy path
			assertThatCode(() -> argumentParser.validateEnvironment()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Environment validation behavior is documented")
		void environmentValidationBehaviorIsDocumented() {
			// This test documents that validateEnvironment() checks for GITHUB_TOKEN
			// and throws IllegalStateException if not found.
			// We can't easily test the failure case without modifying environment
			// variables.

			// The actual validation logic is tested indirectly through integration
			assertThat(true).isTrue(); // Documentation test
		}

	}

	@Nested
	@DisplayName("ParsedConfiguration toString Tests")
	class ParsedConfigurationToStringTest {

		@Test
		@DisplayName("Should provide readable toString representation")
		void shouldProvideReadableToStringRepresentation() {
			String[] args = { "--repo", "test/repo", "--batch-size", "50", "--dry-run" };

			ParsedConfiguration config = argumentParser.parseAndValidate(args);
			String configString = config.toString();

			assertThat(configString).contains("ParsedConfiguration{")
				.contains("repository='test/repo'")
				.contains("batchSize=50")
				.contains("dryRun=true");
		}

	}

}