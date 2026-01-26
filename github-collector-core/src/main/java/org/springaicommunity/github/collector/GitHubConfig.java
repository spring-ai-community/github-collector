package org.springaicommunity.github.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Spring configuration for GitHub API clients and related beans.
 */
@Configuration
public class GitHubConfig {

	@Value("${GITHUB_TOKEN}")
	private String githubToken;

	@Bean
	public GitHub gitHub() throws IOException {
		return new GitHubBuilder().withOAuthToken(githubToken).build();
	}

	@Bean
	public RestClient restClient() {
		return RestClient.builder()
			.defaultHeader("Authorization", "token " + githubToken)
			.defaultHeader("Accept", "application/vnd.github.v3+json")
			.build();
	}

	@Bean
	public RestClient graphQLClient() {
		return RestClient.builder()
			.baseUrl("https://api.github.com/graphql")
			.defaultHeader("Authorization", "Bearer " + githubToken)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
		return mapper;
	}

}