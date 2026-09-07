package com.mugloar.solver.service;

import com.mugloar.solver.dto.AutoplayJobStatus;
import com.mugloar.solver.dto.SolveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class AutoplayJobService {

    private static final Logger log = LoggerFactory.getLogger(AutoplayJobService.class);
    private final ExecutorService executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final GameLoopService loopService;

    public AutoplayJobService(GameLoopService loopService) {
        this.loopService = loopService;
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    public String submit(String gameId, int target) {
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, gameId, Instant.now());
        jobs.put(jobId, job);

        Future<?> future = executor.submit(() -> {
            try {
                job.status = Status.RUNNING;
                log.info("Autoplay job {} started for game {}", jobId, gameId);
                SolveResponse res = loopService.playUntilTarget(gameId, target);
                job.result = res;
                job.status = Status.COMPLETED;
                log.info("Autoplay job {} completed -> score={}, lives={}", jobId, res.score(), res.lives());
            } catch (Exception e) {
                job.status = Status.FAILED;
                job.error = e.getMessage();
                log.warn("Autoplay job {} failed: {}", jobId, e.toString());
            } finally {
                job.finishedAt = Instant.now();
            }
        });
        job.future = future;
        return jobId;
    }

    public AutoplayJobStatus status(String jobId) {
        Job j = jobs.get(jobId);
        if (j == null) return null;
        SolveResponse r = j.result;
        return new AutoplayJobStatus(
                j.jobId,
                j.status.name(),
                r != null ? r.score() : null,
                r != null ? r.lives() : null,
                r != null ? r.turn() : null,
                j.error
        );
    }

    enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    static class Job {
        final String jobId;
        final String gameId;
        final Instant startedAt;
        volatile Instant finishedAt;
        volatile Status status = Status.PENDING;
        volatile Future<?> future;
        volatile SolveResponse result;
        volatile String error;

        Job(String jobId, String gameId, Instant startedAt) {
            this.jobId = jobId;
            this.gameId = gameId;
            this.startedAt = startedAt;
        }
    }
}
