package com.example.cicdmaster.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PipelineRunJobResponse(
        Long historyId,
        UUID jobId,
        UUID stageId,
        String stageName,
        Integer stageOrder,
        Integer jobOrder,
        String jobLabel,
        String status,
        Long duration,
        LocalDateTime startDate,
        String logs,
        Map<String, Object> additionalData
) {
}
