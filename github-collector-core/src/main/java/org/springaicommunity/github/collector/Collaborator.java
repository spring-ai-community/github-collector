package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

/**
 * Represents a GitHub repository collaborator with their permissions.
 *
 * <p>
 * Collaborators are users who have been granted access to a repository beyond the default
 * public access. This record is used to identify maintainers for label authority analysis
 * - determining whether labels were applied by project maintainers or external
 * contributors.
 *
 * <p>
 * The permissions object indicates what actions the collaborator can perform:
 * <ul>
 * <li>{@code admin} - Full repository access including settings</li>
 * <li>{@code maintain} - Manage repository without access to sensitive settings</li>
 * <li>{@code push} - Read and write access (can push commits)</li>
 * <li>{@code triage} - Read access plus manage issues and PRs</li>
 * <li>{@code pull} - Read-only access</li>
 * </ul>
 *
 * @param login the collaborator's GitHub username
 * @param id the collaborator's unique GitHub user ID
 * @param type the account type ("User" or "Bot")
 * @param permissions the collaborator's repository permissions
 * @param roleName the role name (e.g., "admin", "write", "read", "maintain", "triage")
 * @see <a href="https://docs.github.com/en/rest/collaborators/collaborators">GitHub
 * Collaborators API</a>
 */
public record Collaborator(String login, long id, String type, @Nullable Permissions permissions,
		@Nullable String roleName) {

	/**
	 * Repository permission levels for a collaborator.
	 *
	 * @param admin full repository access including settings
	 * @param maintain manage repository without sensitive settings access
	 * @param push read and write access (can push commits)
	 * @param triage read access plus manage issues and PRs
	 * @param pull read-only access
	 */
	public record Permissions(boolean admin, boolean maintain, boolean push, boolean triage, boolean pull) {
	}
}
