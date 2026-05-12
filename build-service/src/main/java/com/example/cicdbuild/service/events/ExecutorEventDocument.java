package com.example.cicdbuild.service.events;

import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    public static ExecutorEventDocument from(ExecutorEventMessage eventMessage, UUID forcedJobId) {
        return new ExecutorEventDocument(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                "build-service",
                eventMessage.eventType(),
                toStringOrNull(eventMessage.pipelineId()),
                toStringOrNull(forcedJobId == null ? eventMessage.jobId() : forcedJobId),
                eventMessage.status(),
                eventMessage.historyId(),
                instantToString(eventMessage.startedAt()),
                instantToString(eventMessage.finishedAt()),
                eventMessage.durationMs(),
                eventMessage.logs(),
                eventMessage.additionalData()
        );
    }

    public static ExecutorEventDocument logFrom(ExecutorEventMessage eventMessage, UUID forcedJobId) {
        return new ExecutorEventDocument(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                "build-service",
                "JOB_LOG",
                toStringOrNull(eventMessage.pipelineId()),
                toStringOrNull(forcedJobId == null ? eventMessage.jobId() : forcedJobId),
                eventMessage.status(),
                eventMessage.historyId(),
                instantToString(eventMessage.startedAt()),
                instantToString(eventMessage.finishedAt()),
                eventMessage.durationMs(),
                eventMessage.logs(),
                logAdditionalData(eventMessage)
        );
    }

    private static String toStringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String instantToString(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Map<String, Object> logAdditionalData(ExecutorEventMessage eventMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (eventMessage.additionalData() != null) {
            data.putAll(eventMessage.additionalData());
        }
        data.put("logOnly", true);
        if (eventMessage.eventType() != null && !eventMessage.eventType().isBlank()) {
            data.put("sourceEventType", eventMessage.eventType());
        }
        return data;
    }
}
