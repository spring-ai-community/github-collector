package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a GitHub pull request with all relevant metadata.
 *
 * <p>
 * This record captures the complete data from a GitHub pull request including its
 * content, state, timestamps, code changes, and associated entities such as comments,
 * labels, and reviews.
 *
 * @param number the unique pull request number within the repository
 * @param title the pull request title (required, never null)
 * @param body the pull request body/description (may be null if not provided)
 * @param state the pull request state ("OPEN", "CLOSED", or "MERGED")
 * @param createdAt when the pull request was created
 * @param updatedAt when the pull request was last updated
 * @param closedAt when the pull request was closed (null if still open)
 * @param mergedAt when the pull request was merged (null if not merged)
 * @param url the API URL for this pull request
 * @param htmlUrl the web URL for viewing this pull request in a browser
 * @param author the user who created the pull request
 * @param comments list of comments on this pull request
 * @param labels list of labels assigned to this pull request
 * @param reviews list of code reviews on this pull request
 * @param draft whether this is a draft pull request
 * @param merged whether this pull request has been merged
 * @param mergeCommitSha the SHA of the merge commit (null if not merged)
 * @param headRef the name of the branch containing the changes
 * @param baseRef the name of the branch the changes are being merged into
 * @param additions number of lines added in this pull request
 * @param deletions number of lines deleted in this pull request
 * @param changedFiles number of files changed in this pull request
 */
public record PullRequest(int number, String title, @Nullable String body, String state, LocalDateTime createdAt,
		LocalDateTime updatedAt, @Nullable LocalDateTime closedAt, @Nullable LocalDateTime mergedAt, String url,
		String htmlUrl, Author author, List<Comment> comments, List<Label> labels, List<Review> reviews, boolean draft,
		boolean merged, @Nullable String mergeCommitSha, @Nullable String headRef, @Nullable String baseRef,
		int additions, int deletions, int changedFiles) {
}