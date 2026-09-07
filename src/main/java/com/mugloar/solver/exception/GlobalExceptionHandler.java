package com.mugloar.solver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MugloarApiException.class)
	public ResponseEntity<Map<String, String>> handleMugloarApi(MugloarApiException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof HttpStatusCodeException) {
			HttpStatusCodeException httpEx = (HttpStatusCodeException) cause;
			int upstreamStatus = httpEx.getStatusCode().value();
			// If the upstream returned 404/410, map to 410 Gone with a clear message so the UI
			// can stop trying to interact with an expired/finished game.
			if (upstreamStatus == HttpStatus.NOT_FOUND.value() || upstreamStatus == HttpStatus.GONE.value()) {
				return ResponseEntity.status(HttpStatus.GONE)
					.body(Map.of("error", "Game not found or already finished", "detail", ex.getMessage()));
			}
		}
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(Map.of("error", "Upstream game API failed", "detail", ex.getMessage()));
	}

	@ExceptionHandler(RestClientException.class)
	public ResponseEntity<Map<String, String>> handleRestClient(RestClientException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(Map.of("error", "Upstream game API unreachable", "detail", ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException ex) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "Invalid request", "detail", ex.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
		return ResponseEntity.internalServerError()
				.body(Map.of("error", "Unexpected server error", "detail", ex.getMessage()));
	}
}