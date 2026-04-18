package com.example.cicdbuild.dto;

import java.util.UUID;

public record StageResponse(
        UUID id,
        UUID pipelineId,
        Integer order,
        String name,
        String description
) {
}
