package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TriggerUpsertRequest(
        @NotNull Long id,
        @NotBlank String name,
        @NotNull UUID pipelineId
) {
}
