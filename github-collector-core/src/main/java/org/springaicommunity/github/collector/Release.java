package org.springaicommunity.github.collector;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * Represents a GitHub release.
 *
 * <p>
 * Releases are created from Git tags and contain release notes that often reference
 * issues and pull requests. This record is used for H4 (External Validity) analysis -
 * validating that issues mentioned in release notes match their labels (e.g., issues in
 * "Bug Fixes" section should have the bug label).
 *
 * <p>
 * The {@code body} field contains the release notes in Markdown format, which can be
 * parsed to extract issue and PR references (e.g., #123, GH-456).
 *
 * @param id the unique GitHub release ID
 * @param tagName the Git tag name (e.g., "v1.0.0", "1.0.0-M1")
 * @param name the release title (may differ from tag name)
 * @param body the release notes in Markdown format (may be null for empty releases)
 * @param draft whether this is a draft release (not yet published)
 * @param prerelease whether this is a pre-release (e.g., milestone, RC)
 * @param createdAt when the release was created
 * @param publishedAt when the release was published (null for drafts)
 * @param author the user who created the release
 * @param htmlUrl the URL to the release page on GitHub
 * @see <a href="https://docs.github.com/en/rest/releases/releases">GitHub Releases
 * API</a>
 */
public record Release(long id, String tagName, @Nullable String name, @Nullable String body, boolean draft,
		boolean prerelease, LocalDateTime createdAt, @Nullable LocalDateTime publishedAt, Author author,
		String htmlUrl) {
}
