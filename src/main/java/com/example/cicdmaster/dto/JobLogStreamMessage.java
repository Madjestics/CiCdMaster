package com.example.cicdmaster.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobLogStreamMessage(
        UUID jobId,
        UUID pipelineId,
        String eventType,
        String status,
        Long historyId,
        String logs,
        Instant timestamp,
        Map<String, Object> additionalData
) {
}