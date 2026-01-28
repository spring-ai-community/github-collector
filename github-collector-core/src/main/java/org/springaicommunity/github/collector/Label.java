package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

/**
 * Represents a GitHub label with styling information.
 *
 * <p>
 * Labels are used to categorize issues and pull requests within a repository.
 *
 * @param name the label name (unique within the repository)
 * @param color the hex color code for the label (without the # prefix)
 * @param description an optional description explaining the label's purpose
 */
public record Label(String name, String color, @Nullable String description) {
}