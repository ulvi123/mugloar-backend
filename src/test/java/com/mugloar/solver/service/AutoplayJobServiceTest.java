package com.mugloar.solver.service;

import com.mugloar.solver.dto.SolveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoplayJobServiceTest {

    @Mock
    private GameLoopService loopService;

    @Test
    void submitsAndCompletesJob() throws Exception {
        when(loopService.playUntilTarget(anyString(), anyInt()))
                .thenReturn(new SolveResponse(true, 5, 10, 1234, 0, 42, "ok"));

        AutoplayJobService svc = new AutoplayJobService(loopService);
        String jobId = svc.submit("game1", 1000);
        assertNotNull(jobId);

        // wait up to 2s for completion (should be immediate since mocked)
        long deadline = System.currentTimeMillis() + 2000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            var st = svc.status(jobId);
            if (st != null && "COMPLETED".equals(st.status())) {
                status = st.status();
                assertEquals(1234, st.score());
                assertEquals(5, st.lives());
                assertEquals(42, st.turn());
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("COMPLETED", status);
    }
}
