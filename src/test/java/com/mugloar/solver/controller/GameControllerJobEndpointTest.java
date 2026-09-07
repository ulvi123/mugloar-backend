package com.mugloar.solver.controller;

import com.mugloar.solver.dto.AutoplayJobStatus;
import com.mugloar.solver.service.AutoplayJobService;
import com.mugloar.solver.client.MugloarClient;
import com.mugloar.solver.service.GameLoopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerJobEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MugloarClient client;

    @MockBean
    private GameLoopService loopService;

    @MockBean
    private AutoplayJobService jobService;

    @Test
    void enqueueReturns202AndJobId() throws Exception {
        when(jobService.submit(anyString(), anyInt())).thenReturn("job-123");

        mockMvc.perform(post("/api/game/abc/autoplay?target=1000"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"));
    }

    @Test
    void jobStatusEndpointReturnsStatus() throws Exception {
        when(jobService.status("job-123"))
                .thenReturn(new AutoplayJobStatus("job-123", "COMPLETED", 1000, 3, 10, null));

        mockMvc.perform(get("/api/game/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(1000));
    }
}
