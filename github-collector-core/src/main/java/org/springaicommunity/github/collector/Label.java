package org.springaicommunity.github.collector;

/**
 * Represents a GitHub label with styling information.
 */
public record Label(String name, String color, String description) {
}