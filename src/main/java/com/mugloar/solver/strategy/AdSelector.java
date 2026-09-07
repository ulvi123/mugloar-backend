package com.mugloar.solver.strategy;

import com.mugloar.solver.dto.Ad;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public final class AdSelector {

	private AdSelector() {}

	/**
	 * Picks the ad with the highest success probability.
	 * Ties broken by higher numeric rewardd
	 */
	public static Optional<Ad> pickBest(Ad[] ads) {
		if (ads == null || ads.length == 0) return Optional.empty();

		return Arrays.stream(ads)
				.max(Comparator
						.comparingInt((Ad a) -> ProbabilityMapper.score(a.probability()))
						.thenComparingInt(AdSelector::parseReward));
	}

	private static int parseReward(Ad ad) {
		try {
			return Integer.parseInt(ad.reward());
		} catch (NumberFormatException | NullPointerException e) {
			return 0;
		}
	}
}