package com.mugloar.solver.strategy;

import com.mugloar.solver.dto.Ad;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdSelectorTest {

	@Test
	void picksHighestProbabilityAd() {
		Ad[] ads = {
				new Ad("1", "low chance", "50", 5, "Risky"),
				new Ad("2", "high chance", "20", 5, "Piece of cake"),
				new Ad("3", "mid chance", "30", 5, "Gambling")
		};
		Optional<Ad> best = AdSelector.pickBest(ads);
		assertTrue(best.isPresent());
		assertEquals("2", best.get().adId());
	}

	@Test
	void breaksTiesByReward() {
		Ad[] ads = {
				new Ad("1", "a", "10", 5, "Sure thing"),
				new Ad("2", "b", "99", 5, "Sure thing")
		};
		assertEquals("2", AdSelector.pickBest(ads).get().adId());
	}

	@Test
	void returnsEmptyForNoAds() {
		assertTrue(AdSelector.pickBest(new Ad[0]).isEmpty());
		assertTrue(AdSelector.pickBest(null).isEmpty());
	}
}