package org.springaicommunity.github.collector;

import java.time.LocalDateTime;

/**
 * Represents a comment on a GitHub issue.
 */
public record Comment(Author author, String body, LocalDateTime createdAt) {
}