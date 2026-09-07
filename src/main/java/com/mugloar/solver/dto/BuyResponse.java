package com.mugloar.solver.dto;


public record BuyResponse(
		boolean shoppingSuccess,
		int gold,
		int lives,
		int level,
		int turn
){}