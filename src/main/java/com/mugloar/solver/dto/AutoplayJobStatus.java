package com.mugloar.solver.dto;

public record AutoplayJobStatus(
        String jobId,
        String status,
        Integer score,
        Integer lives,
        Integer turn,
        String error
) {}
