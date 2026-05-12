package com.example.cicdmaster.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record PipelineRunResponse(
        String runId,
        Instant requestedAt,
        String initiatedBy,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long duration,
        List<PipelineRunJobResponse> jobs
) {
}
