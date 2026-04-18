package com.example.cicdmaster.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record JobHistoryResponse(
        Long id,
        UUID jobId,
        Long duration,
        LocalDateTime startDate,
        String logs,
        Map<String, Object> additionalData
) {
}
