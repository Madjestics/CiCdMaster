package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record PipelineRunRequest(
        @NotNull String initiatedBy,
        Map<String, Object> parameters
) {
}
