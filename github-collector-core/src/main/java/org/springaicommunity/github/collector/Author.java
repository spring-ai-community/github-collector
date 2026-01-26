package org.springaicommunity.github.collector;

/**
 * Represents a GitHub user (author of issues or comments).
 */
public record Author(String login, String name) {
}