package com.mugloar.solver.client;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class MugloarClientConfig {

	@Bean
	public RestTemplate mugloarRestTemplate(
			@Value("${mugloar.api.connect-timeout-ms}") int connectTimeoutMs,
			@Value("${mugloar.api.read-timeout-ms}") int readTimeoutMs) {

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(50);
		connectionManager.setDefaultMaxPerRoute(30);

		var httpClient = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.build();

		var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
		requestFactory.setConnectTimeout(connectTimeoutMs);
		requestFactory.setConnectionRequestTimeout(readTimeoutMs);

		return new RestTemplate(requestFactory);
	}
}