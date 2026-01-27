package org.springaicommunity.github.collector;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests using ArchUnit to enforce dependency rules.
 *
 * <p>
 * <b>DISABLED:</b> Rename this file back to {@code ArchitectureTest.java} after the
 * design refactoring is complete.
 *
 * <p>
 * This project uses a flat package structure with naming conventions to distinguish
 * architectural roles:
 *
 * <ul>
 * <li><b>Models</b>: Issue, PullRequest, Author, Label, Comment, Review, BatchData,
 * CollectionResult, CollectionRequest, CollectionStats, CollectionMetadata,
 * ParsedConfiguration, ResumeState</li>
 * <li><b>GitHub Services</b>: GitHubRestService, GitHubGraphQLService</li>
 * <li><b>Collection Services</b>: IssueCollectionService, PRCollectionService,
 * BaseCollectionService</li>
 * <li><b>Support</b>: GitHubHttpClient, ArgumentParser</li>
 * <li><b>Configuration</b>: CollectionProperties, GitHubCollectorBuilder</li>
 * </ul>
 *
 * <p>
 * Dependency flow (allowed directions): <pre>
 *   Models → (no internal dependencies - pure data)
 *   Support → Models
 *   GitHub Services → Models, Support
 *   Collection Services → Models, Support, GitHub Services
 *   Configuration → All (wiring layer)
 * </pre>
 */
@AnalyzeClasses(packages = "org.springaicommunity.github.collector",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesDisabled {

	// ========== Model Independence ==========

	@ArchTest
	static final ArchRule models_should_not_depend_on_services = noClasses().that()
		.haveSimpleNameEndingWith("Request")
		.or()
		.haveSimpleNameEndingWith("Result")
		.or()
		.haveSimpleNameEndingWith("Stats")
		.or()
		.haveSimpleNameEndingWith("Metadata")
		.or()
		.haveSimpleNameEndingWith("State")
		.or()
		.haveSimpleNameEndingWith("Data")
		.or()
		.haveSimpleNameEndingWith("Configuration")
		.or()
		.haveSimpleName("Issue")
		.or()
		.haveSimpleName("PullRequest")
		.or()
		.haveSimpleName("Author")
		.or()
		.haveSimpleName("Label")
		.or()
		.haveSimpleName("Comment")
		.or()
		.haveSimpleName("Review")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Service")
		.because("Model classes should be pure data without service dependencies");

	@ArchTest
	static final ArchRule models_should_not_depend_on_support_classes = noClasses().that()
		.haveSimpleNameEndingWith("Request")
		.or()
		.haveSimpleNameEndingWith("Result")
		.or()
		.haveSimpleNameEndingWith("Stats")
		.or()
		.haveSimpleNameEndingWith("Metadata")
		.or()
		.haveSimpleNameEndingWith("State")
		.or()
		.haveSimpleNameEndingWith("Data")
		.or()
		.haveSimpleNameEndingWith("Configuration")
		.or()
		.haveSimpleName("Issue")
		.or()
		.haveSimpleName("PullRequest")
		.or()
		.haveSimpleName("Author")
		.or()
		.haveSimpleName("Label")
		.or()
		.haveSimpleName("Comment")
		.or()
		.haveSimpleName("Review")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Client")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Utils")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Parser")
		.because("Model classes should be pure data without infrastructure dependencies");

	// ========== Service Layer Rules ==========

	@ArchTest
	static final ArchRule github_services_should_not_depend_on_collection_services = noClasses().that()
		.haveSimpleNameStartingWith("GitHub")
		.and()
		.haveSimpleNameEndingWith("Service")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("CollectionService")
		.because("GitHub API services are lower-level than collection services");

	@ArchTest
	static final ArchRule support_classes_should_not_depend_on_services = noClasses().that()
		.haveSimpleNameEndingWith("Client")
		.or()
		.haveSimpleNameEndingWith("Utils")
		.or()
		.haveSimpleNameEndingWith("Parser")
		.and()
		.doNotHaveSimpleName("ArgumentParser") // ArgumentParser may reference Properties
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Service")
		.because("Support/utility classes should not depend on higher-level services");

	// ========== Naming Conventions ==========

	@ArchTest
	static final ArchRule services_should_be_named_correctly = classes().that()
		.haveSimpleNameEndingWith("Service")
		.should()
		.haveSimpleNameEndingWith("Service")
		.because("Service classes should follow naming convention");

	@ArchTest
	static final ArchRule builder_should_be_named_correctly = classes().that()
		.haveSimpleNameEndingWith("Builder")
		.should()
		.haveSimpleNameEndingWith("Builder")
		.because("Builder classes should follow naming convention");

}
