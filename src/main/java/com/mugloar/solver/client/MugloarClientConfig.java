package com.mugloar.solver.client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class MugloarClientConfig {

	@Bean
	public RestTemplate mugloarRestTemplate(
			RestTemplateBuilder builder,
			@org.springframework.beans.factory.annotation.Value("${mugloar.api.connect-timeout-ms}") long connectTimeoutMs,
			@org.springframework.beans.factory.annotation.Value("${mugloar.api.read-timeout-ms}") long readTimeoutMs) {
		return builder
				.setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
				.setReadTimeout(Duration.ofMillis(readTimeoutMs))
				.build();
	}
}