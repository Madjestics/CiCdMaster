package com.example.cicdbuild.service.artifacts;

public record ArtifactUploadResult(
        boolean success,
        String artifactPath,
        String targetUrl,
        Integer statusCode,
        String message
) {
}
