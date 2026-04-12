package com.example.cicdmaster.dto;

import java.util.UUID;

public record PipelineResponse(
        UUID id,
        String name,
        String description,
        UUID folderId
) {
}
