package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility service for JsonNode navigation.
 */
@Service
public class JsonNodeUtils {

	private static final Logger logger = LoggerFactory.getLogger(JsonNodeUtils.class);

	public Optional<String> getString(JsonNode node, String... path) {
		JsonNode target = node;
		for (String p : path) {
			target = target.path(p);
		}
		return target.isMissingNode() ? Optional.empty() : Optional.of(target.asText());
	}

	public Optional<Integer> getInt(JsonNode node, String... path) {
		JsonNode target = node;
		for (String p : path) {
			target = target.path(p);
		}
		return target.isMissingNode() ? Optional.empty() : Optional.of(target.asInt());
	}

	public Optional<LocalDateTime> getDateTime(JsonNode node, String... path) {
		return getString(node, path).map(str -> {
			try {
				return LocalDateTime.parse(str.replace("Z", ""));
			}
			catch (Exception e) {
				logger.warn("Failed to parse datetime: {}", str);
				return null;
			}
		});
	}

	public List<JsonNode> getArray(JsonNode node, String... path) {
		JsonNode target = node;
		for (String p : path) {
			target = target.path(p);
		}

		if (target.isArray()) {
			List<JsonNode> result = new ArrayList<>();
			target.forEach(result::add);
			return result;
		}

		return List.of();
	}

}