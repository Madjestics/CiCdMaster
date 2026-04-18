package com.example.cicdbuild.dto;

import java.util.Map;
import java.util.UUID;

public record JobParamsView(
        UUID jobTemplateId,
        Map<String, Object> params
) {
}
