package org.springaicommunity.github.collector;

import java.time.LocalDateTime;

/**
 * Represents a comment on a GitHub issue or pull request.
 *
 * <p>
 * This record captures the essential data from a comment including its author, content,
 * and timestamp.
 *
 * @param author the user who wrote the comment
 * @param body the comment text content
 * @param createdAt when the comment was created
 */
public record Comment(Author author, String body, LocalDateTime createdAt) {
}