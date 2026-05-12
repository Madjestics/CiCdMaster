package com.example.cicdmaster.service.opensearch;

import java.util.Map;

public record ExecutorEventDocument(
        String documentId,
        String ingestedAt,
        String sourceService,
        String eventType,
        String pipelineId,
        String jobId,
        String status,
        Long historyId,
        String startedAt,
        String finishedAt,
        Long durationMs,
        String logs,
        Map<String, Object> additionalData
) {
}
