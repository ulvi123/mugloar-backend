package com.mugloar.solver.dto;

public record GameStartResponse(
		String gameId,
		int lives,
		int gold,
		int score,
		int turn,
		int highScore
) {
}