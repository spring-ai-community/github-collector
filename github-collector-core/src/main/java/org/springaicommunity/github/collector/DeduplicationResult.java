package org.springaicommunity.github.collector;

/**
 * Results of a batch deduplication operation.
 *
 * @param duplicatesRemoved total number of duplicate items removed
 * @param filesRewritten number of batch files that were rewritten with duplicates removed
 * @param filesDeleted number of batch files deleted because they became empty
 * @param filesRenumbered number of batch files renamed to restore sequential numbering
 */
public record DeduplicationResult(int duplicatesRemoved, int filesRewritten, int filesDeleted, int filesRenumbered) {
}
