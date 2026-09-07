package com.mugloar.solver.service;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.*;
import com.mugloar.solver.strategy.AdSelector;
import com.mugloar.solver.strategy.ProbabilityMapper;
import com.mugloar.solver.exception.MugloarApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class GameLoopService {

	private static final Logger log = LoggerFactory.getLogger(GameLoopService.class);
	private static final int MAX_TURNS_SAFETY_CAP = 2000;
	private static final int MAX_CONSECUTIVE_FAILURES = 5;


	private final MugloarClient client;

	public GameLoopService(MugloarClient client) {
		this.client = client;
	}

	/**
	 * Plays a single existing game until lives run out, the target score is hit,
	 * or the safety cap is reached. Returns the last known solve result.
	 */
	public SolveResponse playUntilTarget(String gameId, int targetScore) {
		SolveResponse last = null;
		int turns = 0;
		int consecutiveFailures = 0;

		while (turns++ < MAX_TURNS_SAFETY_CAP) {
			Ad[] ads;
			try {
				ads = client.getAds(gameId);
			} catch (MugloarApiException e) {
				consecutiveFailures++;
				log.warn("Game {}: failed to fetch ads on turn {} (consecutive failure {}/{}): {}",
						gameId, turns, consecutiveFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage());
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					log.error("Game {}: giving up after {} consecutive failures — game likely ended or expired server-side",
							gameId, consecutiveFailures);
					break;
				}
				continue;
			}

			Optional<Ad> best = AdSelector.pickBest(ads);
			if (best.isEmpty()) {
				log.warn("No ads available for game {}, stopping", gameId);
				break;
			}

			try {
				last = client.solve(gameId, best.get().adId());
				consecutiveFailures = 0;
			} catch (MugloarApiException e) {
				consecutiveFailures++;
				log.warn("Game {}: failed to solve ad {} on turn {} (consecutive failure {}/{}): {}",
						gameId, best.get().adId(), turns, consecutiveFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage());
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					log.error("Game {}: giving up after {} consecutive failures — game likely ended or expired server-side",
							gameId, consecutiveFailures);
					break;
				}
				continue;
			}

			log.info("Game {}: solved ad {} (probability='{}', mappedScore={}) -> success={}, score={}, lives={}, gold={}",
					gameId, best.get().adId(), best.get().probability(), ProbabilityMapper.score(best.get().probability()),
					last.success(), last.score(), last.lives(), last.gold());

			if (last.lives() <= 0) break;
			if (last.score() >= targetScore) break;

			maybeBuyLifePotion(gameId, last);
		}

		return last;
	}

	/**
	 * Starts a brand new game and plays it to the target score. Used by the
	 * standalone scripting-challenge entry point as well as the /autoplay endpoint.
	 */
	public SolveResponse playNewGame(int targetScore) {
		GameStartResponse start = client.startGame();
		log.info("Started new game {}", start.gameId());
		return playUntilTarget(start.gameId(), targetScore);
	}

	private void maybeBuyLifePotion(String gameId, SolveResponse state) {
		if (state.gold() < 50) return;

		ShopItem[] shop;
		try {
			shop = client.getShop(gameId);
		} catch (MugloarApiException e) {
			log.warn("Game {}: failed to fetch shop, skipping potion purchase this turn: {}", gameId, e.getMessage());
			return;
		}
		Arrays.stream(shop)
				.filter(item -> containsAny(item.name(), "heal", "life"))
				.filter(item -> item.cost() <= state.gold())
				.findFirst()
				.ifPresent(item -> {
					try {
						log.info("Game {}: buying {} for {} gold (lives={} -> banking buffer)", gameId, item.name(), item.cost(), state.lives());
						client.buy(gameId, item.id());
					} catch (MugloarApiException e) {
						log.warn("Game {}: failed to buy {}, continuing without it: {}", gameId, item.id(), e.getMessage());
					}
				});
	}

	private boolean containsAny(String haystack, String... needles) {
		if (haystack == null) return false;
		String lower = haystack.toLowerCase();
		for (String n : needles) {
			if (lower.contains(n)) return true;
		}
		return false;
	}
}