package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a GitHub issue with all relevant metadata.
 *
 * <p>
 * This record captures the essential data from a GitHub issue including its content,
 * state, timestamps, and associated entities.
 *
 * @param number the unique issue number within the repository
 * @param title the issue title (required, never null)
 * @param body the issue body/description (may be null if not provided)
 * @param state the issue state ("OPEN" or "CLOSED")
 * @param createdAt when the issue was created
 * @param updatedAt when the issue was last updated
 * @param closedAt when the issue was closed (null if still open)
 * @param url the API URL for this issue
 * @param author the user who created the issue
 * @param comments list of comments on this issue
 * @param labels list of labels assigned to this issue
 * @param events list of timeline events (label changes, state changes, etc.)
 */
public record Issue(int number, String title, @Nullable String body, String state, LocalDateTime createdAt,
		LocalDateTime updatedAt, @Nullable LocalDateTime closedAt, String url, Author author, List<Comment> comments,
		List<Label> labels, List<IssueEvent> events) {
}