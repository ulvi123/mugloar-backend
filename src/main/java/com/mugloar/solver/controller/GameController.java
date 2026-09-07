package com.mugloar.solver.controller;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.*;
import com.mugloar.solver.service.GameLoopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GameController {

	private final MugloarClient client;
	private final GameLoopService loopService;
	private static final Logger log = LoggerFactory.getLogger(GameController.class);


	public GameController(MugloarClient client, GameLoopService loopService) {
		this.client = client;
		this.loopService = loopService;
	}

	@PostMapping("/start")
	public GameStartResponse start() {
		GameStartResponse response = client.startGame();
		log.info("Started game {}", response.gameId());
		return response;
	}

	@GetMapping("/{gameId}/ads")
	public Ad[] ads(@PathVariable String gameId) {
		Ad[] ads = client.getAds(gameId);
		log.info("Game {}: fetched {} ads", gameId, ads.length);
		return ads;
	}

	@PostMapping("/{gameId}/solve/{adId}")
	public SolveResponse solve(@PathVariable String gameId, @PathVariable String adId) {
		SolveResponse result = client.solve(gameId, adId);
		log.info("Game {}: [manual] solved ad {} -> success={}, score={}, lives={}, gold={}",
				gameId, adId, result.success(), result.score(), result.lives(), result.gold());
		return result;
	}

	@GetMapping("/{gameId}/shop")
	public ShopItem[] shop(@PathVariable String gameId) {
		ShopItem[] items = client.getShop(gameId);
		log.info("Game {}: fetched shop ({} items)", gameId, items.length);
		return items;
	}

	@PostMapping("/{gameId}/shop/buy/{itemId}")
	public BuyResponse buy(@PathVariable String gameId, @PathVariable String itemId) {
		BuyResponse result = client.buy(gameId, itemId);
		log.info("Game {}: [manual] bought {} -> success={}, gold={}, lives={}",
				gameId, itemId, result.shoppingSuccess(), result.gold(), result.lives());
		return result;
	}

	@PostMapping("/{gameId}/autoplay")
	public SolveResponse autoplay(@PathVariable String gameId,
	                              @RequestParam(defaultValue = "1000") int target) {
		log.info("Game {}: starting autoplay toward target {}", gameId, target);
		SolveResponse result = loopService.playUntilTarget(gameId, target);
		if (result == null) {
			throw new IllegalStateException("Autoplay did not produce a result — game likely ended or expired");
		}
		return result;
	}
}
