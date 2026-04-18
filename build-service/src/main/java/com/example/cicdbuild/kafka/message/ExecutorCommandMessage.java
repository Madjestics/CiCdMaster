package com.example.cicdbuild.kafka.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutorCommandMessage(
        String commandType,
        UUID pipelineId,
        UUID stageId,
        UUID jobId,
        String initiatedBy,
        Instant requestedAt,
        Map<String, Object> payload
) {
}
