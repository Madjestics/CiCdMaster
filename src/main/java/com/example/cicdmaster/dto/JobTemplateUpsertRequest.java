package com.example.cicdmaster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record JobTemplateUpsertRequest(
        @NotBlank String path,
        @NotNull Map<String, Object> paramsTemplate
) {
}
