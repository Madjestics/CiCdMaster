package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobEntity;
import com.example.cicdmaster.domain.entity.JobHistoryEntity;
import com.example.cicdmaster.domain.entity.StageEntity;
import com.example.cicdmaster.dto.PipelineRunJobResponse;
import com.example.cicdmaster.dto.PipelineRunResponse;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.JobHistoryRepository;
import com.example.cicdmaster.repository.JobParamsRepository;
import com.example.cicdmaster.repository.PipelineRepository;
import com.example.cicdmaster.repository.JobRepository;
import com.example.cicdmaster.repository.StageRepository;
import com.example.cicdmaster.service.opensearch.OpenSearchHistoryLogService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PipelineRunHistoryService {

    private static final String PIPELINE_RUN_COMMAND = "PIPELINE_RUN";

    private final PipelineRepository pipelineRepository;
    private final StageRepository stageRepository;
    private final JobRepository jobRepository;
    private final JobParamsRepository jobParamsRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final ObjectProvider<OpenSearchHistoryLogService> openSearchHistoryLogServiceProvider;

    public List<PipelineRunResponse> findByPipeline(UUID pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + pipelineId));

        Map<UUID, JobMeta> jobMetaById = loadJobMeta(pipelineId);
        List<JobHistoryEntity> history = jobHistoryRepository.findByJobStagePipelineIdOrderByStartDateDesc(pipelineId);
        if (history.isEmpty()) {
            return List.of();
        }

        Map<UUID, Map<Long, String>> logsByJobHistoryId = resolveExternalLogs(history);
        Map<String, RunBucket> runs = new LinkedHashMap<>();

        for (JobHistoryEntity entry : history) {
            if (entry.getJob() == null || entry.getJob().getId() == null || entry.getStartDate() == null) {
                continue;
            }

            Map<String, Object> additionalData = entry.getAdditionalData() == null
                    ? Collections.emptyMap()
                    : entry.getAdditionalData();
            String commandType = normalize(additionalData.get("commandType"));
            if (commandType != null && !PIPELINE_RUN_COMMAND.equals(commandType)) {
                continue;
            }

            Instant requestedAt = resolveRequestedAt(entry.getStartDate(), additionalData);
            String initiatedBy = stringValue(additionalData.get("initiatedBy"));
            String runId = resolveRunId(entry, additionalData, initiatedBy);

            RunBucket bucket = runs.computeIfAbsent(
                    runId,
                    ignored -> new RunBucket(runId, requestedAt, initiatedBy, jobMetaById.size())
            );

            JobMeta jobMeta = jobMetaById.getOrDefault(
                    entry.getJob().getId(),
                    new JobMeta(null, "Неизвестный этап", Integer.MAX_VALUE, Integer.MAX_VALUE, "Задача")
            );

            String externalLogs = Optional.ofNullable(logsByJobHistoryId.get(entry.getJob().getId()))
                    .map(map -> map.get(entry.getId()))
                    .orElse(null);
            String status = resolveJobStatus(additionalData);
            String logs = externalLogs == null ? "" : externalLogs;

            PipelineRunJobResponse jobResponse = new PipelineRunJobResponse(
                    entry.getId(),
                    entry.getJob().getId(),
                    jobMeta.stageId(),
                    jobMeta.stageName(),
                    jobMeta.stageOrder(),
                    jobMeta.jobOrder(),
                    jobMeta.jobLabel(),
                    status,
                    entry.getDuration(),
                    entry.getStartDate(),
                    logs,
                    entry.getAdditionalData()
            );
            bucket.putJob(jobResponse);
        }

        return runs.values().stream()
                .map(RunBucket::toResponse)
                .filter(response -> !response.jobs().isEmpty())
                .sorted(Comparator.comparing(PipelineRunResponse::requestedAt).reversed())
                .toList();
    }

    private Map<UUID, JobMeta> loadJobMeta(UUID pipelineId) {
        Map<UUID, JobMeta> result = new LinkedHashMap<>();
        List<StageEntity> stages = stageRepository.findByPipelineIdOrderByOrderIndexAsc(pipelineId);

        for (StageEntity stage : stages) {
            List<JobEntity> jobs = jobRepository.findByStageIdOrderByOrderIndexAsc(stage.getId());
            for (JobEntity job : jobs) {
                result.put(
                        job.getId(),
                        new JobMeta(
                                stage.getId(),
                                stage.getName(),
                                safeOrder(stage.getOrderIndex()),
                                safeOrder(job.getOrderIndex()),
                                resolveJobLabel(job)
                        )
                );
            }
        }
        return result;
    }

    private String resolveJobLabel(JobEntity job) {
        if (job.getScript() != null && !job.getScript().isBlank()) {
            String singleLine = job.getScript().replaceAll("\\s+", " ").trim();
            if (singleLine.length() > 96) {
                return "Скрипт: " + singleLine.substring(0, 96) + "...";
            }
            return "Скрипт: " + singleLine;
        }
        return jobParamsRepository.findTemplatePathByJobId(job.getId())
                .map(path -> "Шаблон: " + path)
                .orElse("Шаблонная задача");
    }

    private Map<UUID, Map<Long, String>> resolveExternalLogs(List<JobHistoryEntity> history) {
        OpenSearchHistoryLogService openSearchHistoryLogService = openSearchHistoryLogServiceProvider.getIfAvailable();
        if (openSearchHistoryLogService == null) {
            return Map.of();
        }

        Map<UUID, List<Long>> historyIdsByJob = new LinkedHashMap<>();
        for (JobHistoryEntity entry : history) {
            if (entry.getJob() == null || entry.getJob().getId() == null || entry.getId() == null) {
                continue;
            }
            historyIdsByJob.computeIfAbsent(entry.getJob().getId(), ignored -> new ArrayList<>()).add(entry.getId());
        }

        Map<UUID, Map<Long, String>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<Long>> item : historyIdsByJob.entrySet()) {
            Map<Long, String> logs = openSearchHistoryLogService.resolveLatestLogsByHistoryId(item.getKey(), item.getValue());
            if (!logs.isEmpty()) {
                result.put(item.getKey(), logs);
            }
        }
        return result;
    }

    private Instant resolveRequestedAt(LocalDateTime startDate, Map<String, Object> additionalData) {
        Object requestedAt = additionalData.get("requestedAt");
        String asString = requestedAt == null ? null : String.valueOf(requestedAt);
        if (asString != null && !asString.isBlank()) {
            try {
                return Instant.parse(asString);
            } catch (Exception ignored) {
                // no-op
            }
        }
        return startDate.toInstant(ZoneOffset.UTC);
    }

    private String resolveRunId(
            JobHistoryEntity entry,
            Map<String, Object> additionalData,
            String initiatedBy
    ) {
        String key = stringValue(additionalData.get("requestedAt"));
        if (key != null && !key.isBlank()) {
            return "run-" + key;
        }

        LocalDateTime rounded = entry.getStartDate().truncatedTo(ChronoUnit.MINUTES);
        String who = initiatedBy == null || initiatedBy.isBlank() ? "unknown" : initiatedBy;
        return "legacy-" + who + "-" + rounded;
    }

    private String resolveJobStatus(Map<String, Object> additionalData) {
        String explicitStatus = normalize(additionalData.get("eventStatus"));
        if (explicitStatus != null) {
            return explicitStatus;
        }

        String phase = normalize(additionalData.get("phase"));
        if (phase == null) {
            String result = normalize(additionalData.get("result"));
            if (result == null) {
                return "UNKNOWN";
            }
            return switch (result) {
                case "SUCCESS" -> "SUCCESS";
                case "FAILED" -> "FAILED";
                case "CANCELED" -> "CANCELED";
                default -> "UNKNOWN";
            };
        }

        return switch (phase) {
            case "QUEUED" -> "QUEUED";
            case "RUNNING" -> "RUNNING";
            case "SUCCESS" -> "SUCCESS";
            case "FAILED" -> "FAILED";
            case "CANCELED" -> "CANCELED";
            case "FINISHED" -> "SUCCESS";
            default -> "UNKNOWN";
        };
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value).trim();
        if (asString.isBlank()) {
            return null;
        }
        return asString.toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value).trim();
        return asString.isBlank() ? null : asString;
    }

    private int safeOrder(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private record JobMeta(
            UUID stageId,
            String stageName,
            Integer stageOrder,
            Integer jobOrder,
            String jobLabel
    ) {
    }

    private static final class RunBucket {
        private final String runId;
        private final Instant requestedAt;
        private final String initiatedBy;
        private final int expectedJobCount;
        private final Map<UUID, PipelineRunJobResponse> jobsById = new LinkedHashMap<>();
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;

        private RunBucket(String runId, Instant requestedAt, String initiatedBy, int expectedJobCount) {
            this.runId = runId;
            this.requestedAt = requestedAt;
            this.initiatedBy = initiatedBy;
            this.expectedJobCount = expectedJobCount;
        }

        private void putJob(PipelineRunJobResponse job) {
            PipelineRunJobResponse existing = jobsById.get(job.jobId());
            if (existing == null || isAfter(job.startDate(), existing.startDate())) {
                jobsById.put(job.jobId(), job);
            }
            if (startedAt == null || isBefore(job.startDate(), startedAt)) {
                startedAt = job.startDate();
            }
            LocalDateTime jobFinishedAt = resolveJobFinishedAt(job);
            if (finishedAt == null || isAfter(jobFinishedAt, finishedAt)) {
                finishedAt = jobFinishedAt;
            }
        }

        private PipelineRunResponse toResponse() {
            List<PipelineRunJobResponse> jobs = new ArrayList<>(jobsById.values());
            jobs.sort(Comparator
                    .comparing(PipelineRunJobResponse::stageOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(PipelineRunJobResponse::jobOrder, Comparator.nullsLast(Integer::compareTo)));

            Long duration = null;
            if (startedAt != null && finishedAt != null) {
                duration = Math.max(0, ChronoUnit.MILLIS.between(startedAt, finishedAt));
            }

            return new PipelineRunResponse(
                    runId,
                    requestedAt,
                    initiatedBy,
                    resolveRunStatus(jobs, expectedJobCount),
                    startedAt,
                    finishedAt,
                    duration,
                    jobs
            );
        }

        private static String resolveRunStatus(Collection<PipelineRunJobResponse> jobs, int expectedJobCount) {
            if (jobs.isEmpty()) {
                return "UNKNOWN";
            }

            boolean hasFailed = jobs.stream().anyMatch(job -> Objects.equals(job.status(), "FAILED"));
            if (hasFailed) {
                return "FAILED";
            }

            boolean hasCanceled = jobs.stream().anyMatch(job -> Objects.equals(job.status(), "CANCELED"));
            if (hasCanceled) {
                return "CANCELED";
            }

            boolean allSuccess = jobs.stream().allMatch(job -> Objects.equals(job.status(), "SUCCESS"));
            if (allSuccess) {
                return expectedJobCount <= 0 || jobs.size() >= expectedJobCount ? "SUCCESS" : "RUNNING";
            }

            boolean inProgress = jobs.stream().anyMatch(job ->
                    Objects.equals(job.status(), "RUNNING")
                            || Objects.equals(job.status(), "QUEUED")
                            || Objects.equals(job.status(), "PENDING"));
            if (inProgress) {
                return "RUNNING";
            }

            return "UNKNOWN";
        }

        private static LocalDateTime resolveJobFinishedAt(PipelineRunJobResponse job) {
            if (job.startDate() == null) {
                return null;
            }
            long duration = job.duration() == null ? 0L : Math.max(0L, job.duration());
            return job.startDate().plus(duration, ChronoUnit.MILLIS);
        }

        private static boolean isAfter(LocalDateTime left, LocalDateTime right) {
            if (left == null) {
                return false;
            }
            if (right == null) {
                return true;
            }
            return left.isAfter(right);
        }

        private static boolean isBefore(LocalDateTime left, LocalDateTime right) {
            if (left == null) {
                return false;
            }
            if (right == null) {
                return true;
            }
            return left.isBefore(right);
        }
    }
}
