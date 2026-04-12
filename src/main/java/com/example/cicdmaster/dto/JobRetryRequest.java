package com.example.cicdmaster.dto;

import java.util.Map;

public record JobRetryRequest(
        Map<String, Object> runtimeOverrides
) {
}
