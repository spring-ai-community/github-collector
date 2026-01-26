package org.springaicommunity.github.collector;

import java.time.LocalDateTime;

/**
 * Represents a GitHub pull request review.
 */
public record Review(long id, String body, String state, // APPROVED, CHANGES_REQUESTED,
															// COMMENTED, DISMISSED
		LocalDateTime submittedAt, Author author, String authorAssociation, // MEMBER,
																			// CONTRIBUTOR,
																			// FIRST_TIME_CONTRIBUTOR,
																			// etc.
		String htmlUrl) {
}