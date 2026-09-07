package com.mugloar.solver.controller;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.*;
import com.mugloar.solver.service.GameLoopService;
import com.mugloar.solver.service.AutoplayJobService;
import com.mugloar.solver.dto.AutoplayJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

	private final MugloarClient client;
	private final GameLoopService loopService;
	private final AutoplayJobService jobService;
	private static final Logger log = LoggerFactory.getLogger(GameController.class);


	public GameController(MugloarClient client, GameLoopService loopService, AutoplayJobService jobService) {
		this.client = client;
		this.loopService = loopService;
		this.jobService = jobService;
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
	public ResponseEntity<Map<String, String>> autoplay(@PathVariable String gameId,
	                                                    @RequestParam(defaultValue = "1000") int target) {
		log.info("Game {}: enqueueing autoplay toward target {}", gameId, target);
		String jobId = jobService.submit(gameId, target);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
	}

	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<AutoplayJobStatus> jobStatus(@PathVariable String jobId) {
		AutoplayJobStatus status = jobService.status(jobId);
		if (status == null) return ResponseEntity.notFound().build();
		return ResponseEntity.ok(status);
	}
}
