package com.example.cicdmaster.dto;

import com.example.cicdmaster.domain.enums.JobStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record JobUpsertRequest(
        @NotNull UUID stageId,
        @NotNull Integer order,
        @NotNull JobStatus status,
        String script,
        boolean scriptPrimary,
        UUID jobTemplateId,
        Map<String, Object> params
) {
}
