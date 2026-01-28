package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for creating a consistently configured {@link ObjectMapper}.
 *
 * <p>
 * The returned mapper uses {@link PropertyNamingStrategies#SNAKE_CASE} so that Java
 * camelCase record fields are serialized as snake_case JSON keys
 * (e.g.&nbsp;{@code createdAt} &rarr; {@code created_at}).
 */
public final class ObjectMapperFactory {

	private ObjectMapperFactory() {
	}

	/**
	 * Create a new {@link ObjectMapper} with standard configuration.
	 * @return configured ObjectMapper
	 */
	public static ObjectMapper create() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		return mapper;
	}

}
