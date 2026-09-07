package com.mugloar.solver.client;

import com.mugloar.solver.dto.*;
import com.mugloar.solver.exception.MugloarApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class MugloarClient {

	private final RestTemplate restTemplate;
	private final String baseUrl;

	public MugloarClient(RestTemplate mugloarRestTemplate,
	                     @Value("${mugloar.api.base-url}") String baseUrl) {
		this.restTemplate = mugloarRestTemplate;
		this.baseUrl = baseUrl;
	}

	public GameStartResponse startGame() {
		try {
			GameStartResponse response = restTemplate.postForObject(
					baseUrl + "/game/start", null, GameStartResponse.class);
			if (response == null) throw new MugloarApiException("Empty response from game/start");
			return response;
		} catch (RestClientException e) {
			throw new MugloarApiException("Failed to start game", e);
		}
	}

	public Ad[] getAds(String gameId) {
		validateGameId(gameId);
		try {
			Ad[] ads = restTemplate.getForObject(baseUrl + "/" + gameId + "/messages", Ad[].class);
			return ads != null ? ads : new Ad[0];
		} catch (RestClientException e) {
			throw new MugloarApiException("Failed to fetch ads for game " + gameId, e);
		}
	}

	public SolveResponse solve(String gameId, String adId) {
		validateGameId(gameId);
		if (adId == null || adId.isBlank()) {
			throw new IllegalArgumentException("adId must not be blank");
		}
		try {
			org.springframework.http.ResponseEntity<SolveResponse> resp = restTemplate.exchange(
					baseUrl + "/" + gameId + "/solve/" + adId,
					org.springframework.http.HttpMethod.POST,
					org.springframework.http.HttpEntity.EMPTY,
					SolveResponse.class);

			org.springframework.http.HttpStatus status = (org.springframework.http.HttpStatus) resp.getStatusCode();
			SolveResponse body = resp.getBody();

			if (status.is2xxSuccessful()) {
				if (body == null) {
					throw new MugloarApiException("Empty response from solve",
						new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.GONE, "Empty body"));
				}
				return body;
			} else if (status == org.springframework.http.HttpStatus.NOT_FOUND || status == org.springframework.http.HttpStatus.GONE) {
				throw new MugloarApiException("Failed to solve ad " + adId,
					new org.springframework.web.client.HttpClientErrorException(status, "Upstream returned " + status));
			} else {
				throw new MugloarApiException("Failed to solve ad " + adId + ": upstream returned " + status);
			}
		} catch (org.springframework.web.client.RestClientException e) {
			throw new MugloarApiException("Failed to solve ad " + adId, e);
		}
	}

	public ShopItem[] getShop(String gameId) {
		validateGameId(gameId);
		try {
			ShopItem[] items = restTemplate.getForObject(baseUrl + "/" + gameId + "/shop", ShopItem[].class);
			return items != null ? items : new ShopItem[0];
		} catch (RestClientException e) {
			throw new MugloarApiException("Failed to fetch shop for game " + gameId, e);
		}
	}

	public BuyResponse buy(String gameId, String itemId) {
		validateGameId(gameId);
		if (itemId == null || itemId.isBlank()) {
			throw new IllegalArgumentException("itemId must not be blank");
		}
		try {
			BuyResponse response = restTemplate.postForObject(
					baseUrl + "/" + gameId + "/shop/buy/" + itemId, null, BuyResponse.class);
			if (response == null) throw new MugloarApiException("Empty response from buy");
			return response;
		} catch (RestClientException e) {
			throw new MugloarApiException("Failed to buy item " + itemId, e);
		}
	}

	private void validateGameId(String gameId) {
		if (gameId == null || gameId.isBlank()) {
			throw new IllegalArgumentException("gameId must not be blank");
		}
	}
}