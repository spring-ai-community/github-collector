package org.springaicommunity.github.collector;

/**
 * Represents a soft approval on a pull request.
 *
 * <p>
 * A soft approval is an approval from a non-member (CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR).
 *
 * @param reviewer the GitHub username of the reviewer
 * @param association the author association (e.g., CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR)
 * @param submittedAt when the review was submitted
 */
public record SoftApproval(String reviewer, String association, String submittedAt) {
}
