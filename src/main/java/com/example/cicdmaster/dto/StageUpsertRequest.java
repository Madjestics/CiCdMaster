package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StageUpsertRequest(
        @NotNull UUID pipelineId,
        @NotNull Integer order,
        @NotBlank String name,
        String description
) {
}
