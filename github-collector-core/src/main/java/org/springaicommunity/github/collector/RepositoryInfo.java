package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

/**
 * Basic repository information from the GitHub API.
 *
 * <p>
 * This record captures essential repository metadata, replacing the dependency on
 * org.kohsuke.github.GHRepository.
 *
 * @param id the unique repository ID
 * @param name the repository name (without owner)
 * @param fullName the full repository name in "owner/repo" format
 * @param description the repository description (may be null)
 * @param htmlUrl the web URL for the repository
 * @param isPrivate whether the repository is private
 * @param defaultBranch the default branch name
 */
public record RepositoryInfo(long id, String name, String fullName, @Nullable String description, String htmlUrl,
		boolean isPrivate, String defaultBranch) {

}
