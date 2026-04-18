package com.example.cicdmaster.kafka.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
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
