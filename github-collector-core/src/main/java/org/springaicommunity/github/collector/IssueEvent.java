package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Represents a GitHub issue event from the issue timeline.
 *
 * <p>
 * Issue events track changes to issues over time, including label additions/removals,
 * state changes, assignments, and more. This record is essential for analyzing label
 * authority (who applies labels) and label stability (label churn after issue closure).
 *
 * <p>
 * Common event types include:
 * <ul>
 * <li>{@code labeled} - A label was added to the issue</li>
 * <li>{@code unlabeled} - A label was removed from the issue</li>
 * <li>{@code closed} - The issue was closed</li>
 * <li>{@code reopened} - The issue was reopened</li>
 * <li>{@code assigned} - A user was assigned to the issue</li>
 * <li>{@code unassigned} - A user was unassigned from the issue</li>
 * </ul>
 *
 * @param id the unique event identifier
 * @param event the event type (e.g., "labeled", "unlabeled", "closed")
 * @param actor the user who triggered the event
 * @param label the label involved (only present for "labeled" and "unlabeled" events)
 * @param createdAt when the event occurred
 * @see <a href="https://docs.github.com/en/rest/issues/events">GitHub Issue Events
 * API</a>
 */
public record IssueEvent(long id, String event, Author actor, @Nullable Label label, LocalDateTime createdAt) {
}
