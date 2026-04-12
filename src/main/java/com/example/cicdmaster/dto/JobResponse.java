package com.example.cicdmaster.dto;

import com.example.cicdmaster.domain.enums.JobStatus;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID stageId,
        Integer order,
        JobStatus status,
        String script,
        boolean scriptPrimary,
        JobParamsView params
) {
}
