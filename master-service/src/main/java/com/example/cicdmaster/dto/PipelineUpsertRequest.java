package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PipelineUpsertRequest(
        @NotBlank String name,
        String description,
        UUID folderId
) {
}
