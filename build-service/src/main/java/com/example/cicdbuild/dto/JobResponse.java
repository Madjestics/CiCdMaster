package com.example.cicdbuild.dto;

import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID stageId,
        Integer order,
        String status,
        String script,
        boolean scriptPrimary,
        JobParamsView params
) {
}
