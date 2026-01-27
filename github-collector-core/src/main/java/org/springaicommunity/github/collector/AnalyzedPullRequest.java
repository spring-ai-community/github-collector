package org.springaicommunity.github.collector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A pull request with soft approval analysis results.
 *
 * <p>
 * Contains all PullRequest fields plus analysis metadata.
 *
 * @param number PR number
 * @param title PR title
 * @param body PR body/description
 * @param state PR state (open, closed)
 * @param createdAt when the PR was created
 * @param updatedAt when the PR was last updated
 * @param closedAt when the PR was closed (null if open)
 * @param mergedAt when the PR was merged (null if not merged)
 * @param url API URL
 * @param htmlUrl Web URL
 * @param author PR author
 * @param comments PR comments
 * @param labels PR labels
 * @param reviews PR reviews
 * @param draft whether PR is a draft
 * @param merged whether PR is merged
 * @param mergeCommitSha merge commit SHA (null if not merged)
 * @param headRef head branch ref
 * @param baseRef base branch ref
 * @param additions lines added
 * @param deletions lines deleted
 * @param changedFiles number of files changed
 * @param softApprovalDetected whether soft approval was detected
 * @param analysisTimestamp when analysis was performed
 * @param softApprovals list of soft approvals found
 */
public record AnalyzedPullRequest(int number, String title, String body, String state, LocalDateTime createdAt,
		LocalDateTime updatedAt, LocalDateTime closedAt, LocalDateTime mergedAt, String url, String htmlUrl,
		Author author, List<Comment> comments, List<Label> labels, List<Review> reviews, boolean draft, boolean merged,
		String mergeCommitSha, String headRef, String baseRef, int additions, int deletions, int changedFiles,
		boolean softApprovalDetected, String analysisTimestamp, List<SoftApproval> softApprovals) {

	/**
	 * Create an AnalyzedPullRequest from a PullRequest with analysis results.
	 * @param pr the original pull request
	 * @param softApprovalDetected whether soft approval was found
	 * @param analysisTimestamp when analysis was performed
	 * @param softApprovals list of soft approvals
	 * @return analyzed pull request
	 */
	public static AnalyzedPullRequest from(PullRequest pr, boolean softApprovalDetected, String analysisTimestamp,
			List<SoftApproval> softApprovals) {
		return new AnalyzedPullRequest(pr.number(), pr.title(), pr.body(), pr.state(), pr.createdAt(), pr.updatedAt(),
				pr.closedAt(), pr.mergedAt(), pr.url(), pr.htmlUrl(), pr.author(), pr.comments(), pr.labels(),
				pr.reviews(), pr.draft(), pr.merged(), pr.mergeCommitSha(), pr.headRef(), pr.baseRef(), pr.additions(),
				pr.deletions(), pr.changedFiles(), softApprovalDetected, analysisTimestamp, softApprovals);
	}

}
