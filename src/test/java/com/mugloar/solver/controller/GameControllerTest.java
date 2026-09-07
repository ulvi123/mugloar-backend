package com.mugloar.solver.controller;

import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.dto.GameStartResponse;
import com.mugloar.solver.service.GameLoopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MugloarClient client;

	@MockBean
	private GameLoopService loopService;

	@Test
	void startReturnsGameState() throws Exception {
		when(client.startGame()).thenReturn(new GameStartResponse("abc123", 3, 0, 0, 0, 0));

		mockMvc.perform(post("/api/game/start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value("abc123"))
				.andExpect(jsonPath("$.lives").value(3));
	}
}