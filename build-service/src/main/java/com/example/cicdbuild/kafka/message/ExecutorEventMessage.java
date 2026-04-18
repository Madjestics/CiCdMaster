package com.example.cicdbuild.kafka.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutorEventMessage(
        String eventType,
        UUID pipelineId,
        UUID jobId,
        String status,
        Long historyId,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String logs,
        Map<String, Object> additionalData
) {
}
