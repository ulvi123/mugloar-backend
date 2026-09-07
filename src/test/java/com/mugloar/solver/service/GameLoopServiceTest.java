package com.mugloar.solver.service;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.*;
import com.mugloar.solver.exception.MugloarApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameLoopServiceTest {

	@Mock
	private MugloarClient client;

	@Test
	void stopsWhenLivesReachZero() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };
		when(client.getAds("game1")).thenReturn(ads);
		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(false, 0, 50, 100, 100, 1, "you died"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertEquals(0, result.lives());
		verify(client, times(1)).solve(anyString(), anyString());
	}

	@Test
	void stopsWhenTargetScoreReached() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };
		when(client.getAds("game1")).thenReturn(ads);
		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(true, 3, 50, 1000, 1000, 1, "nice"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertEquals(1000, result.score());
		verify(client, times(1)).solve(anyString(), anyString());
	}

	@Test
	void buysLifePotionWheneverAffordable() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };
		when(client.getAds("game1")).thenReturn(ads).thenReturn(new Ad[0]);
		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(true, 3, 100, 500, 500, 1, "climbing"));  // lives=3, not low
		when(client.getShop("game1"))
				.thenReturn(new ShopItem[]{ new ShopItem("hpot", "Healing potion", 50, null) });

		service.playUntilTarget("game1", 1000);

		verify(client).buy("game1", "hpot");   // now buys even though lives weren't low
	}

	@Test
	void doesNotBuyWhenGoldInsufficient() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };
		when(client.getAds("game1")).thenReturn(ads).thenReturn(new Ad[0]);
		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(true, 3, 40, 500, 500, 1, "climbing"));
		lenient().when(client.getShop("game1"))
				.thenReturn(new ShopItem[]{ new ShopItem("hpot", "Healing potion", 50, null) });

		service.playUntilTarget("game1", 1000);

		verify(client, never()).buy(anyString(), anyString());
	}

	@Test
	void returnsNullWhenNoAdsAvailableFromTheStart() {
		GameLoopService service = new GameLoopService(client);

		when(client.getAds("game1")).thenReturn(new Ad[0]);

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertNull(result);
		verify(client, never()).solve(anyString(), anyString());
	}

	@Test
	void continuesPastTransientSolveFailureInsteadOfAbortingTheRun() {
		GameLoopService service = new GameLoopService(client);

		Ad[] firstFetch = { new Ad("badId", "msg", "10", 5, "Piece of cake") };
		Ad[] secondFetch = { new Ad("goodId", "msg", "10", 5, "Piece of cake") };

		when(client.getAds("game1")).thenReturn(firstFetch).thenReturn(secondFetch);
		when(client.solve("game1", "badId")).thenThrow(new MugloarApiException("Failed to solve ad badId"));
		when(client.solve("game1", "goodId"))
				.thenReturn(new SolveResponse(true, 3, 50, 1000, 1000, 2, "nice"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertEquals(1000, result.score());
		verify(client).solve("game1", "badId");
		verify(client).solve("game1", "goodId");
	}

	@Test
	void givesUpAfterRepeatedConsecutiveFailuresInsteadOfLoopingForever() {
		GameLoopService service = new GameLoopService(client);

		when(client.getAds("game1")).thenThrow(new MugloarApiException("Failed to fetch ads for game game1"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertNull(result);
		verify(client, times(5)).getAds("game1");   // exactly 5 attempts, not 2000
	}

	@Test
	void resetsFailureCountAfterASuccessInBetween() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };

		when(client.getAds("game1"))
				.thenThrow(new MugloarApiException("fail 1"))
				.thenThrow(new MugloarApiException("fail 2"))
				.thenReturn(ads)                      // success resets the counter to not go into loop
				.thenThrow(new MugloarApiException("fail 3"))
				.thenThrow(new MugloarApiException("fail 4"))
				.thenThrow(new MugloarApiException("fail 5"))
				.thenThrow(new MugloarApiException("fail 6"))
				.thenThrow(new MugloarApiException("fail 7"));

		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(true, 3, 10, 10, 10, 1, "ok"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertEquals(10, result.score());
	}

	@Test
	void doesNotResetFailureCountJustBecauseAdsFetchSucceedsWhileSolveKeepsFailing() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("badAd", "msg", "10", 5, "Piece of cake") };

		when(client.getAds("game1")).thenReturn(ads);
		when(client.solve("game1", "badAd")).thenThrow(new MugloarApiException("Failed to solve ad badAd"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertNull(result);
		verify(client, times(5)).solve("game1", "badAd");   // exactly 5, not 91
	}

	@Test
	void continuesPlayingWhenShopFetchFailsInsteadOfAbortingRun() {
		GameLoopService service = new GameLoopService(client);

		Ad[] ads = { new Ad("a1", "msg", "10", 5, "Piece of cake") };
		when(client.getAds("game1")).thenReturn(ads).thenReturn(new Ad[0]);
		when(client.solve("game1", "a1"))
				.thenReturn(new SolveResponse(true, 3, 100, 500, 500, 1, "climbing"));
		when(client.getShop("game1")).thenThrow(new MugloarApiException("Failed to fetch shop"));

		SolveResponse result = service.playUntilTarget("game1", 1000);

		assertEquals(500, result.score());
		verify(client, never()).buy(anyString(), anyString());
	}


}