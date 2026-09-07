package com.mugloar.solver.service;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void throwsWhenNoAdsInitially() {
	    GameLoopService service = new GameLoopService(client);

	    when(client.getAds("game1")).thenReturn(new Ad[0]);

	    assertThrows(com.mugloar.solver.exception.MugloarApiException.class,
	            () -> service.playUntilTarget("game1", 1000));
	}
}