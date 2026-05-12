package com.example.cicdbuild.service.artifacts;

import com.example.cicdbuild.config.BuildServiceProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtifactPublisher {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final BuildServiceProperties buildServiceProperties;

    public ArtifactUploadResult upload(ArtifactPublication publication) {
        if (publication == null || isBlank(publication.uploadUrl()) || isBlank(publication.artifactPath())) {
            return null;
        }

        try {
            Path artifact = resolveArtifact(publication);
            URI target = resolveTargetUri(publication.uploadUrl(), artifact);
            HttpRequest request = buildRequest(publication, artifact, target);
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(resolveUploadTimeout())
                    .build()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                return new ArtifactUploadResult(
                        success,
                        artifact.toString(),
                        target.toString(),
                        response.statusCode(),
                        success ? "Artifact uploaded" : trimResponse(response.body())
                );
            }
        } catch (IOException ex) {
            return buildErrorResult(publication, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return buildErrorResult(publication, ex);
        }
    }

    private HttpRequest buildRequest(ArtifactPublication publication, Path artifact, URI target) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(resolveUploadTimeout())
                .header("Content-Type", firstNotBlank(publication.contentType(), DEFAULT_CONTENT_TYPE))
                .PUT(HttpRequest.BodyPublishers.ofFile(artifact));

        String username = firstNotBlank(publication.username(), buildServiceProperties.getRepositoryManager().getUsername());
        String password = firstNotBlank(publication.password(), buildServiceProperties.getRepositoryManager().getPassword());
        if (!isBlank(username) || !isBlank(password)) {
            String token = Base64.getEncoder()
                    .encodeToString((nullToEmpty(username) + ":" + nullToEmpty(password)).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }

        return builder.build();
    }

    private Path resolveArtifact(ArtifactPublication publication) throws IOException {
        Path workingDirectory = publication.workingDirectory().toAbsolutePath().normalize();
        if (containsGlob(publication.artifactPath())) {
            return resolveByGlob(workingDirectory, publication.artifactPath())
                    .orElseThrow(() -> new IllegalStateException("Artifact not found by pattern: " + publication.artifactPath()));
        }

        Path artifact = workingDirectory.resolve(publication.artifactPath()).normalize();
        if (!artifact.startsWith(workingDirectory)) {
            throw new IllegalStateException("Artifact path leaves the working directory: " + publication.artifactPath());
        }
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalStateException("Artifact file not found: " + artifact);
        }
        return artifact;
    }

    private Optional<Path> resolveByGlob(Path workingDirectory, String pattern) throws IOException {
        String normalizedPattern = pattern.replace('\\', '/');
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);

        try (Stream<Path> stream = Files.walk(workingDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(normalizedRelativePath(workingDirectory, path)))
                    .max(Comparator.comparing(this::lastModifiedMillis));
        }
    }


    private ArtifactUploadResult buildErrorResult(ArtifactPublication publication, Exception exception) {
        return new ArtifactUploadResult(
                false,
                publication.artifactPath(),
                publication.uploadUrl(),
                null,
                exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
        );
    }

    private Path normalizedRelativePath(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return Path.of(relative);
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private URI resolveTargetUri(String uploadUrl, Path artifact) {
        String target = uploadUrl.trim();
        if (target.endsWith("/")) {
            target += URLEncoder.encode(artifact.getFileName().toString(), StandardCharsets.UTF_8);
        }
        return URI.create(target);
    }

    private Duration resolveUploadTimeout() {
        long seconds = Math.max(1L, buildServiceProperties.getRepositoryManager().getUploadTimeoutSeconds());
        return Duration.ofSeconds(seconds);
    }

    private boolean containsGlob(String value) {
        return value != null && (value.contains("*") || value.contains("?") || value.contains("[") || value.contains("{"));
    }

    private String trimResponse(String body) {
        if (body == null || body.isBlank()) {
            return "Repository manager rejected artifact upload";
        }
        String singleLine = body.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 256 ? singleLine.substring(0, 256) + "..." : singleLine;
    }

    private String firstNotBlank(String left, String right) {
        return !isBlank(left) ? left : right;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
