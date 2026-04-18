package com.example.cicdbuild.dto;

import java.util.Map;
import java.util.UUID;

public record JobTemplateResponse(
        UUID id,
        String path,
        Map<String, Object> paramsTemplate
) {
}
