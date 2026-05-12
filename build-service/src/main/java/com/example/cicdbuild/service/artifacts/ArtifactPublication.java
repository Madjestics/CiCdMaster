package com.example.cicdbuild.service.artifacts;

import java.nio.file.Path;

public record ArtifactPublication(
        Path workingDirectory,
        String artifactPath,
        String uploadUrl,
        String username,
        String password,
        String contentType
) {
}
