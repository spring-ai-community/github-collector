package org.springaicommunity.github.collector;

import org.jspecify.annotations.Nullable;

/**
 * Represents a GitHub user (author of issues, pull requests, comments, or reviews).
 *
 * <p>
 * This record captures the essential user identity information from GitHub.
 *
 * @param login the GitHub username (unique identifier, never null)
 * @param name the user's display name (may be null if not set in their profile)
 */
public record Author(String login, @Nullable String name) {
}