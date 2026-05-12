package com.example.cicdmaster.service;

import com.example.cicdmaster.config.AppKafkaProperties;
import com.example.cicdmaster.domain.entity.JobEntity;
import com.example.cicdmaster.domain.entity.StageEntity;
import com.example.cicdmaster.domain.enums.JobStatus;
import com.example.cicdmaster.kafka.message.ExecutorCommandMessage;
import com.example.cicdmaster.kafka.message.ExecutorEventMessage;
import com.example.cicdmaster.repository.JobHistoryRepository;
import com.example.cicdmaster.repository.JobParamsRepository;
import com.example.cicdmaster.repository.JobRepository;
import com.example.cicdmaster.repository.StageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.mock-executor", name = "enabled", havingValue = "true")
public class MockExecutorService {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final StageRepository stageRepository;
    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final JobParamsRepository jobParamsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppKafkaProperties kafkaProperties;

    private final AtomicLong historySequence = new AtomicLong(0L);
    private final ConcurrentMap<UUID, AtomicBoolean> cancellationByPipeline = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> cancellationReasonByPipeline = new ConcurrentHashMap<>();
    private final ExecutorService executorPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("mock-executor-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void initSequence() {
        historySequence.set(jobHistoryRepository.findMaxId());
    }

    @PreDestroy
    void shutdown() {
        executorPool.shutdownNow();
    }

    public void handleCommand(ExecutorCommandMessage commandMessage) {
        if (commandMessage == null || commandMessage.commandType() == null) {
            return;
        }

        String commandType = commandMessage.commandType().toUpperCase(Locale.ROOT);
        switch (commandType) {
            case "PIPELINE_RUN" -> handlePipelineRun(commandMessage);
            case "PIPELINE_CANCEL" -> handlePipelineCancel(commandMessage);
            case "JOB_RETRY" -> handleJobRetry(commandMessage);
            default -> {
                // ignore unknown command types
            }
        }
    }

    private void handlePipelineRun(ExecutorCommandMessage commandMessage) {
        if (commandMessage.pipelineId() == null) {
            return;
        }

        cancellationByPipeline.put(commandMessage.pipelineId(), new AtomicBoolean(false));
        cancellationReasonByPipeline.remove(commandMessage.pipelineId());
        executorPool.submit(() -> executePipeline(commandMessage));
    }

    private void executePipeline(ExecutorCommandMessage commandMessage) {
        UUID pipelineId = commandMessage.pipelineId();
        try {
            List<StageEntity> stages = stageRepository.findByPipelineIdOrderByOrderIndexAsc(pipelineId);
            for (StageEntity stage : stages) {
                List<JobEntity> jobs = jobRepository.findByStageIdOrderByOrderIndexAsc(stage.getId());
                for (JobEntity job : jobs) {
                    if (isPipelineCanceled(pipelineId)) {
                        publishCanceledBeforeStart(job, pipelineId, commandMessage);
                        continue;
                    }
                    simulateJobLifecycle(job, pipelineId, commandMessage, false);
                }
            }
        } finally {
            cancellationByPipeline.remove(pipelineId);
            cancellationReasonByPipeline.remove(pipelineId);
        }
    }

    private void handleJobRetry(ExecutorCommandMessage commandMessage) {
        if (commandMessage.jobId() == null || commandMessage.pipelineId() == null) {
            return;
        }

        jobRepository.findById(commandMessage.jobId()).ifPresent(job ->
                executorPool.submit(() -> simulateJobLifecycle(job, commandMessage.pipelineId(), commandMessage, true)));
    }

    private void handlePipelineCancel(ExecutorCommandMessage commandMessage) {
        if (commandMessage.pipelineId() == null) {
            return;
        }

        UUID pipelineId = commandMessage.pipelineId();
        cancellationByPipeline.computeIfAbsent(pipelineId, ignored -> new AtomicBoolean(false)).set(true);
        cancellationReasonByPipeline.put(pipelineId, extractReason(commandMessage));
    }

    private void simulateJobLifecycle(
            JobEntity job,
            UUID pipelineId,
            ExecutorCommandMessage commandMessage,
            boolean retry
    ) {
        publishEvent(
                job.getId(),
                new ExecutorEventMessage(
                        "JOB_QUEUED",
                        pipelineId,
                        job.getId(),
                        JobStatus.QUEUED.name(),
                        null,
                        null,
                        null,
                        null,
                        buildQueuedLogs(job, commandMessage, retry),
                        buildAdditionalData(commandMessage, retry, "queued", null)
                )
        );

        pause(kafkaProperties.getMockExecutor().getQueueDelayMs());
        if (isPipelineCanceled(pipelineId)) {
            publishCanceledEvent(
                    job,
                    pipelineId,
                    historySequence.incrementAndGet(),
                    Instant.now(),
                    resolveCancelReason(pipelineId),
                    "JOB_CANCELED",
                    commandMessage,
                    retry
            );
            return;
        }

        long historyId = historySequence.incrementAndGet();
        Instant startedAt = Instant.now();
        publishEvent(
                job.getId(),
                new ExecutorEventMessage(
                        "JOB_RUNNING",
                        pipelineId,
                        job.getId(),
                        JobStatus.RUNNING.name(),
                        historyId,
                        startedAt,
                        null,
                        null,
                        buildRunningLogs(job, commandMessage, retry, false),
                        buildAdditionalData(commandMessage, retry, "running", null)
                )
        );

        pause(kafkaProperties.getMockExecutor().getLogChunkDelayMs());
        if (isPipelineCanceled(pipelineId)) {
            publishCanceledEvent(
                    job,
                    pipelineId,
                    historyId,
                    startedAt,
                    resolveCancelReason(pipelineId),
                    "JOB_CANCELED",
                    commandMessage,
                    retry
            );
            return;
        }

        publishEvent(
                job.getId(),
                new ExecutorEventMessage(
                        "JOB_RUNNING",
                        pipelineId,
                        job.getId(),
                        JobStatus.RUNNING.name(),
                        historyId,
                        startedAt,
                        null,
                        null,
                        buildRunningLogs(job, commandMessage, retry, true),
                        buildAdditionalData(commandMessage, retry, "running", null)
                )
        );

        pause(kafkaProperties.getMockExecutor().getRunDelayMs());
        if (isPipelineCanceled(pipelineId)) {
            publishCanceledEvent(
                    job,
                    pipelineId,
                    historyId,
                    startedAt,
                    resolveCancelReason(pipelineId),
                    "JOB_CANCELED",
                    commandMessage,
                    retry
            );
            return;
        }

        Instant finishedAt = Instant.now();
        long durationMs = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        publishEvent(
                job.getId(),
                new ExecutorEventMessage(
                        "JOB_FINISHED",
                        pipelineId,
                        job.getId(),
                        JobStatus.SUCCESS.name(),
                        historyId,
                        startedAt,
                        finishedAt,
                        durationMs,
                        buildSuccessLogs(job, retry, durationMs),
                        buildAdditionalData(commandMessage, retry, "success", null)
                )
        );
    }

    private void publishCanceledBeforeStart(JobEntity job, UUID pipelineId, ExecutorCommandMessage commandMessage) {
        publishCanceledEvent(
                job,
                pipelineId,
                historySequence.incrementAndGet(),
                Instant.now(),
                resolveCancelReason(pipelineId),
                "JOB_SKIPPED_BY_CANCEL",
                commandMessage,
                false
        );
    }

    private void publishCanceledEvent(
            JobEntity job,
            UUID pipelineId,
            long historyId,
            Instant startedAt,
            String reason,
            String eventType,
            ExecutorCommandMessage commandMessage,
            boolean retry
    ) {
        Instant finishedAt = Instant.now();
        long durationMs = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        Map<String, Object> additionalData = buildAdditionalData(commandMessage, retry, "canceled", reason);
        additionalData.put("result", "canceled");
        publishEvent(
                job.getId(),
                new ExecutorEventMessage(
                        eventType,
                        pipelineId,
                        job.getId(),
                        JobStatus.CANCELED.name(),
                        historyId,
                        startedAt,
                        finishedAt,
                        durationMs,
                        buildCanceledLogs(job, reason),
                        additionalData
                )
        );
    }

    private Map<String, Object> buildAdditionalData(
            ExecutorCommandMessage commandMessage,
            boolean retry,
            String phase,
            String reason
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mock", true);
        data.put("phase", phase);
        data.put("retry", retry);
        data.put("commandType", commandMessage.commandType());
        if (commandMessage.requestedAt() != null) {
            data.put("requestedAt", commandMessage.requestedAt());
        }
        if (commandMessage.initiatedBy() != null && !commandMessage.initiatedBy().isBlank()) {
            data.put("initiatedBy", commandMessage.initiatedBy());
        }
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason);
        }
        return data;
    }

    private String buildQueuedLogs(JobEntity job, ExecutorCommandMessage commandMessage, boolean retry) {
        return String.join(
                "\n",
                line("INFO", "Получена команда " + commandMessage.commandType() + " для задачи " + job.getId()),
                line("INFO", retry ? "Повторный запуск задачи добавлен в очередь." : "Задача добавлена в очередь исполнителя.")
        );
    }

    private String buildRunningLogs(
            JobEntity job,
            ExecutorCommandMessage commandMessage,
            boolean retry,
            boolean secondChunk
    ) {
        if (secondChunk) {
            return String.join(
                "\n",
                line("INFO", "Подключение артефактов и подготовка окружения завершены."),
                line("INFO", "Выполняемый шаг: " + readableScript(job)),
                line("DEBUG", "Параметры запуска: " + stringifyPayload(commandMessage.payload()))
            );
        }

        return String.join(
                "\n",
                line("INFO", retry ? "Повторный запуск задачи начат." : "Запуск задачи начат."),
                line("INFO", "Инициализация рабочего каталога..."),
                line("INFO", "Проверка доступности зависимостей выполнена.")
        );
    }

    private String buildSuccessLogs(JobEntity job, boolean retry, long durationMs) {
        return String.join(
                "\n",
                line("INFO", retry ? "Повторное выполнение завершено успешно." : "Выполнение задачи завершено успешно."),
                line("INFO", "Длительность: " + durationMs + " ms"),
                line("INFO", "Выполненный шаг: " + readableScript(job)),
                line("INFO", "Артефакты переданы в следующий этап.")
        );
    }

    private String buildCanceledLogs(JobEntity job, String reason) {
        String resolvedReason = (reason == null || reason.isBlank()) ? "manual_cancel" : reason;
        return String.join(
                "\n",
                line("WARN", "Задача остановлена по запросу управляющего сервиса."),
                line("WARN", "Причина остановки: " + resolvedReason),
                line("INFO", "Остановленный шаг: " + readableScript(job))
        );
    }

    private String stringifyPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        return payload.toString();
    }

    private String readableScript(JobEntity job) {
        if (job.getScript() == null || job.getScript().isBlank()) {
            String templatePath = jobParamsRepository.findTemplatePathByJobId(job.getId()).orElse(null);
            if (templatePath == null || templatePath.isBlank()) {
                return "Шаблонная задача без явного пути";
            }
            return "Шаблон " + templatePath + " (" + describeTemplatePath(templatePath) + ")";
        }
        return job.getScript().replaceAll("\\s+", " ").trim();
    }

    private String describeTemplatePath(String templatePath) {
        return switch (templatePath) {
            case "vsc/git" -> "загрузка исходного кода из Git";
            case "vsc/mercurial" -> "загрузка исходного кода из Mercurial";
            case "build/maven" -> "сборка проекта Maven";
            case "build/gradle" -> "сборка проекта Gradle";
            case "build/javac" -> "компиляция Java (javac)";
            case "build/gcc" -> "компиляция C/C++ (gcc)";
            case "fuzzing" -> "фаззинг и проверка входных данных";
            case "deploy/windows/cmd" -> "деплой на Windows через CMD";
            case "deploy/linux/bash" -> "деплой на Linux через Bash";
            default -> "выполнение шага по шаблону";
        };
    }

    private String extractReason(ExecutorCommandMessage commandMessage) {
        if (commandMessage.payload() == null) {
            return "manual_cancel";
        }
        Object value = commandMessage.payload().get("reason");
        return value == null ? "manual_cancel" : String.valueOf(value);
    }

    private String resolveCancelReason(UUID pipelineId) {
        return cancellationReasonByPipeline.getOrDefault(pipelineId, "manual_cancel");
    }

    private boolean isPipelineCanceled(UUID pipelineId) {
        AtomicBoolean flag = cancellationByPipeline.get(pipelineId);
        return flag != null && flag.get();
    }

    private void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String line(String level, String message) {
        return Instant.now() + " [" + level + "] " + message;
    }

    private void publishEvent(UUID jobId, ExecutorEventMessage eventMessage) {
        String key = jobId == null ? UUID.randomUUID().toString() : jobId.toString();
        kafkaTemplate.send(kafkaProperties.getTopics().getExecutorEvents(), key, eventMessage);
    }
}
