package org.springaicommunity.github.collector;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests using ArchUnit to enforce dependency rules and layering.
 *
 * <p>
 * This project follows a layered architecture with clear interface boundaries:
 *
 * <h3>Interfaces (Contracts)</h3>
 * <ul>
 * <li>{@link GitHubClient} - HTTP operations for GitHub API</li>
 * <li>{@link CollectionStateRepository} - Persistence operations</li>
 * <li>{@link ArchiveService} - Archive creation</li>
 * <li>{@link BatchStrategy} - Batch sizing logic</li>
 * </ul>
 *
 * <h3>Implementations</h3>
 * <ul>
 * <li>{@link GitHubHttpClient} - Default HTTP implementation (includes logging)</li>
 * <li>{@link RetryingGitHubClient} - Retry decorator with exponential backoff</li>
 * <li>{@link FileSystemStateRepository} - File system persistence</li>
 * <li>{@link ZipArchiveService} - ZIP archive creation</li>
 * <li>{@link FixedBatchStrategy}, {@link AdaptiveBatchStrategy} - Batch strategies</li>
 * </ul>
 *
 * <h3>Dependency Rules</h3> <pre>
 *   Services → Interfaces (NOT concrete implementations)
 *   Decorators → Interface they decorate
 *   Collection Services → BaseCollectionService (extends)
 * </pre>
 */
@AnalyzeClasses(packages = "org.springaicommunity.github.collector",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	// ========== Interface Dependency Rules ==========

	@ArchTest
	static final ArchRule github_services_should_depend_on_client_interface = noClasses().that()
		.haveSimpleNameEndingWith("Service")
		.and()
		.doNotHaveSimpleName("BaseCollectionService")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("GitHubHttpClient")
		.because("Services should depend on GitHubClient interface, not the concrete GitHubHttpClient");

	@ArchTest
	static final ArchRule collection_services_should_not_depend_on_file_system_repository = noClasses().that()
		.haveSimpleNameEndingWith("CollectionService")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("FileSystemStateRepository")
		.because("Collection services should depend on CollectionStateRepository interface");

	@ArchTest
	static final ArchRule collection_services_should_not_depend_on_zip_archive_service = noClasses().that()
		.haveSimpleNameEndingWith("CollectionService")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("ZipArchiveService")
		.because("Collection services should depend on ArchiveService interface");

	@ArchTest
	static final ArchRule collection_services_should_not_depend_on_concrete_batch_strategies = noClasses().that()
		.haveSimpleNameEndingWith("CollectionService")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("FixedBatchStrategy")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleName("AdaptiveBatchStrategy")
		.because("Collection services should depend on BatchStrategy interface");

	// ========== No Direct I/O Bypass Rules ==========

	@ArchTest
	static final ArchRule collection_services_should_not_use_direct_file_operations = noClasses().that()
		.haveSimpleNameEndingWith("CollectionService")
		.should()
		.dependOnClassesThat()
		.belongToAnyOf(java.io.FileOutputStream.class, java.io.FileInputStream.class)
		.because("Collection services should use CollectionStateRepository for file I/O");

	@ArchTest
	static final ArchRule collection_services_should_not_use_zip_directly = noClasses().that()
		.haveSimpleNameEndingWith("CollectionService")
		.should()
		.accessClassesThat()
		.resideInAPackage("java.util.zip")
		.because("Collection services should use ArchiveService for ZIP operations");

	// ========== Decorator Rules ==========

	@ArchTest
	static final ArchRule github_client_decorators_should_implement_interface = classes().that()
		.haveSimpleNameEndingWith("GitHubClient")
		.and()
		.doNotHaveSimpleName("GitHubClient")
		.should()
		.implement(GitHubClient.class)
		.because("All *GitHubClient classes should implement the GitHubClient interface");

	@ArchTest
	static final ArchRule decorators_should_not_depend_on_concrete_http_client = noClasses().that()
		.haveSimpleName("RetryingGitHubClient")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("GitHubHttpClient")
		.because("Decorators should depend on the GitHubClient interface, not concrete implementation");

	// ========== Hierarchy Rules ==========

	@ArchTest
	static final ArchRule issue_collection_service_should_extend_base = classes().that()
		.haveSimpleName("IssueCollectionService")
		.should()
		.beAssignableTo(BaseCollectionService.class)
		.because("IssueCollectionService must extend BaseCollectionService");

	@ArchTest
	static final ArchRule pr_collection_service_should_extend_base = classes().that()
		.haveSimpleName("PRCollectionService")
		.should()
		.beAssignableTo(BaseCollectionService.class)
		.because("PRCollectionService must extend BaseCollectionService");

	// ========== Implementation Rules ==========

	@ArchTest
	static final ArchRule batch_strategies_should_implement_interface = classes().that()
		.haveSimpleNameEndingWith("BatchStrategy")
		.and()
		.doNotHaveSimpleName("BatchStrategy")
		.should()
		.implement(BatchStrategy.class)
		.because("All *BatchStrategy classes should implement the BatchStrategy interface");

	@ArchTest
	static final ArchRule state_repositories_should_implement_interface = classes().that()
		.haveSimpleNameEndingWith("StateRepository")
		.and()
		.doNotHaveSimpleName("CollectionStateRepository")
		.should()
		.implement(CollectionStateRepository.class)
		.because("All *StateRepository classes should implement CollectionStateRepository interface");

	@ArchTest
	static final ArchRule archive_services_should_implement_interface = classes().that()
		.haveSimpleNameEndingWith("ArchiveService")
		.and()
		.doNotHaveSimpleName("ArchiveService")
		.should()
		.implement(ArchiveService.class)
		.because("All *ArchiveService classes should implement ArchiveService interface");

	// ========== Model Independence (from original test) ==========

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

	// ========== Service Layer Rules (from original test) ==========

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
		.haveSimpleNameEndingWith("Utils")
		.or()
		.haveSimpleNameEndingWith("Parser")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Service")
		.because("Support/utility classes should not depend on higher-level services");

	// ========== Builder/Configuration Rules ==========

	@ArchTest
	static final ArchRule only_builder_should_instantiate_concrete_implementations = noClasses().that()
		.doNotHaveSimpleName("GitHubCollectorBuilder")
		.and()
		.haveSimpleNameEndingWith("Service")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("FileSystemStateRepository")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleName("ZipArchiveService")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleName("FixedBatchStrategy")
		.orShould()
		.dependOnClassesThat()
		.haveSimpleName("AdaptiveBatchStrategy")
		.because("Only GitHubCollectorBuilder should create concrete implementations");

}
