package org.springaicommunity.github.collector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a GitHub pull request with all relevant metadata.
 */
public record PullRequest(int number, String title, String body, String state, LocalDateTime createdAt,
		LocalDateTime updatedAt, LocalDateTime closedAt, LocalDateTime mergedAt, String url, String htmlUrl,
		Author author, List<Comment> comments, List<Label> labels, List<Review> reviews, boolean draft, boolean merged,
		String mergeCommitSha, String headRef, String baseRef, int additions, int deletions, int changedFiles) {
}