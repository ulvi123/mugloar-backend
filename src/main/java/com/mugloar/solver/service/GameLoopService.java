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
	private static final int MAX_TURNS_SAFETY_CAP = 2000; // guards against infinite loop on API weirdness

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

		while (turns++ < MAX_TURNS_SAFETY_CAP) {
			Ad[] ads = client.getAds(gameId);
			Optional<Ad> best = AdSelector.pickBest(ads);
			if (best.isEmpty()) {
				// If no ads are available on the very first fetch, treat this as an upstream API problem
				// rather than returning a null result which serializes to empty JSON fields.
				if (last == null) {
					throw new MugloarApiException("No ads available for game " + gameId);
				}
				log.warn("No ads available for game {}, stopping", gameId);
				break;
			}

			Ad chosen = best.get();
			last = client.solve(gameId, chosen.adId());
			log.info("Game {}: solved ad {} (probability='{}', mappedScore={}) -> success={}, score={}, lives={}, gold={}",
					gameId, chosen.adId(), chosen.probability(), ProbabilityMapper.score(chosen.probability()),
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
		if ( state.gold() < 50) return;

		ShopItem[] shop = client.getShop(gameId);
		Arrays.stream(shop)
				.filter(item -> containsAny(item.name(), "heal", "life"))
				.filter(item -> item.cost() <= state.gold())
				.findFirst()
				.ifPresent(item -> {
					log.info("Game {}: buying {} for {} gold (lives={})", gameId, item.name(), item.cost(), state.lives());
					client.buy(gameId, item.id());
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