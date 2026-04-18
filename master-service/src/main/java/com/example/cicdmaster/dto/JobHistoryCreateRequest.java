package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record JobHistoryCreateRequest(
        @NotNull Long id,
        @NotNull UUID jobId,
        @NotNull Long duration,
        @NotNull LocalDateTime startDate,
        @NotBlank String logs,
        Map<String, Object> additionalData
) {
}
