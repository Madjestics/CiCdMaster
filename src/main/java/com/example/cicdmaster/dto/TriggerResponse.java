package com.example.cicdmaster.dto;

import java.util.UUID;

public record TriggerResponse(
        Long id,
        String name,
        UUID pipelineId
) {
}
