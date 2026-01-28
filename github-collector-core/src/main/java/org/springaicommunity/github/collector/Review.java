package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Represents a GitHub pull request review.
 *
 * <p>
 * This record captures the data from a code review on a pull request, including the
 * reviewer's verdict and their relationship to the repository.
 *
 * @param id the unique review identifier
 * @param body the review comment body (may be null if no comment provided)
 * @param state the review state: "APPROVED", "CHANGES_REQUESTED", "COMMENTED", or
 * "DISMISSED"
 * @param submittedAt when the review was submitted (null if pending)
 * @param author the user who submitted the review
 * @param authorAssociation the reviewer's relationship to the repository: "MEMBER",
 * "CONTRIBUTOR", "FIRST_TIME_CONTRIBUTOR", "COLLABORATOR", "OWNER", "MANNEQUIN", or
 * "NONE"
 * @param htmlUrl the web URL for viewing this review in a browser
 */
public record Review(long id, @Nullable String body, String state, @Nullable LocalDateTime submittedAt, Author author,
		String authorAssociation, String htmlUrl) {
}
