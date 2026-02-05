package org.springaicommunity.github.collector;

import java.util.List;

/**
 * Results of a batch verification operation.
 *
 * <p>
 * Contains lists of detected issues across four verification checks: duplicates, date
 * range violations, state mismatches, and batch integrity problems.
 *
 * @param duplicates items that appear in more than one batch file
 * @param dateRangeViolations items with created_at outside the expected date range
 * @param stateViolations items whose state does not match the expected state
 * @param integrityIssues batch-level problems (count mismatches, numbering gaps)
 * @param filesScanned number of batch files that were scanned
 * @param totalItems total number of items across all batch files
 */
public record VerificationResult(List<DuplicateEntry> duplicates, List<DateRangeViolation> dateRangeViolations,
		List<StateViolation> stateViolations, List<BatchIntegrityIssue> integrityIssues, int filesScanned,
		int totalItems) {

	/**
	 * Returns true if all verification checks passed with no issues detected.
	 */
	public boolean passed() {
		return duplicates.isEmpty() && dateRangeViolations.isEmpty() && stateViolations.isEmpty()
				&& integrityIssues.isEmpty();
	}

	/**
	 * An item that appears in multiple batch files.
	 *
	 * @param itemNumber the issue/PR number or release/collaborator id
	 * @param foundInFiles list of batch filenames containing this item
	 */
	public record DuplicateEntry(long itemNumber, List<String> foundInFiles) {
	}

	/**
	 * An item whose created_at timestamp falls outside the expected date range.
	 *
	 * @param itemNumber the issue/PR number
	 * @param createdAt the item's created_at value
	 * @param fileName the batch file containing the item
	 * @param reason description of the violation
	 */
	public record DateRangeViolation(long itemNumber, String createdAt, String fileName, String reason) {
	}

	/**
	 * An item whose state does not match the expected collection state.
	 *
	 * @param itemNumber the issue/PR number
	 * @param expectedState the state that was expected
	 * @param actualState the state found on the item
	 * @param fileName the batch file containing the item
	 */
	public record StateViolation(long itemNumber, String expectedState, String actualState, String fileName) {
	}

	/**
	 * A batch-level integrity problem.
	 *
	 * @param fileName the batch file with the issue
	 * @param issue description of the integrity problem
	 */
	public record BatchIntegrityIssue(String fileName, String issue) {
	}

}
