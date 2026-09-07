package com.mugloar.solver.dto;


public record Ad(
		String adId,
		String message,
		String reward,
		int expiresIn,
		String probability
) {
};