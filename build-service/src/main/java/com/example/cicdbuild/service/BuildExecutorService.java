package com.example.cicdbuild.service;

import com.example.cicdbuild.config.AppKafkaProperties;
import com.example.cicdbuild.config.BuildServiceProperties;
import com.example.cicdbuild.dto.JobResponse;
import com.example.cicdbuild.dto.JobTemplateResponse;
import com.example.cicdbuild.dto.StageResponse;
import com.example.cicdbuild.kafka.message.ExecutorCommandMessage;
import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuildExecutorService {

    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private final MasterApiClient masterApiClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppKafkaProperties kafkaProperties;
    private final BuildServiceProperties buildServiceProperties;

    private final AtomicLong historySequence = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentMap<UUID, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> cancellationReasons = new ConcurrentHashMap<>();
    private final ExecutorService executorPool = Executors.newCachedThreadPool();

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
                // skip unknown command
            }
        }
    }

    private void handlePipelineRun(ExecutorCommandMessage commandMessage) {
        if (commandMessage.pipelineId() == null) {
            return;
        }
        cancellationFlags.put(commandMessage.pipelineId(), new AtomicBoolean(false));
        cancellationReasons.remove(commandMessage.pipelineId());
        executorPool.submit(() -> executePipeline(commandMessage));
    }

    private void handlePipelineCancel(ExecutorCommandMessage commandMessage) {
        if (commandMessage.pipelineId() == null) {
            return;
        }
        cancellationFlags.computeIfAbsent(commandMessage.pipelineId(), ignored -> new AtomicBoolean(false)).set(true);
        cancellationReasons.put(commandMessage.pipelineId(), extractCancelReason(commandMessage));
    }

    private void handleJobRetry(ExecutorCommandMessage commandMessage) {
        if (commandMessage.jobId() == null || commandMessage.pipelineId() == null) {
            return;
        }
        executorPool.submit(() -> executeRetriedJob(commandMessage));
    }

    private void executePipeline(ExecutorCommandMessage commandMessage) {
        UUID pipelineId = commandMessage.pipelineId();
        ExecutionContext context = new ExecutionContext(pipelineId, resolvePipelineWorkspace(pipelineId));

        try {
            List<StageResponse> stages = masterApiClient.fetchStagesByPipeline(pipelineId).stream()
                    .sorted(Comparator.comparingInt(stage -> safeOrder(stage.order())))
                    .toList();

            boolean stopExecution = false;
            for (StageResponse stage : stages) {
                List<JobResponse> jobs = masterApiClient.fetchJobsByStage(stage.id()).stream()
                        .sorted(Comparator.comparingInt(job -> safeOrder(job.order())))
                        .toList();

                for (JobResponse job : jobs) {
                    if (isCanceled(pipelineId)) {
                        publishSkipped(job, pipelineId, resolveCancelReason(pipelineId));
                        continue;
                    }

                    boolean success = executeJob(commandMessage, job, context, false);
                    if (!success) {
                        cancellationFlags.computeIfAbsent(pipelineId, ignored -> new AtomicBoolean(false)).set(true);
                        cancellationReasons.putIfAbsent(pipelineId, "Сборка остановлена после ошибки задачи " + job.id());
                        stopExecution = true;
                        break;
                    }
                }

                if (stopExecution) {
                    break;
                }
            }
        } catch (Exception ex) {
            // no global pipeline event in current contract; failures are reported by per-job events
        } finally {
            cancellationFlags.remove(pipelineId);
            cancellationReasons.remove(pipelineId);
        }
    }

    private void executeRetriedJob(ExecutorCommandMessage commandMessage) {
        UUID pipelineId = commandMessage.pipelineId();
        ExecutionContext context = new ExecutionContext(pipelineId, resolvePipelineWorkspace(pipelineId));
        JobResponse job = masterApiClient.fetchJob(commandMessage.jobId());
        executeJob(commandMessage, job, context, true);
    }

    private boolean executeJob(
            ExecutorCommandMessage commandMessage,
            JobResponse job,
            ExecutionContext context,
            boolean retry
    ) {
        UUID pipelineId = context.pipelineId();

        publishEvent(
                job.id(),
                new ExecutorEventMessage(
                        "JOB_QUEUED",
                        pipelineId,
                        job.id(),
                        "QUEUED",
                        null,
                        null,
                        null,
                        null,
                        line("INFO", retry ? "Повторная постановка задачи в очередь." : "Задача поставлена в очередь исполнителя."),
                        additionalData(commandMessage, "queued", null, null, null)
                )
        );

        if (isCanceled(pipelineId)) {
            publishSkipped(job, pipelineId, resolveCancelReason(pipelineId));
            return false;
        }

        long historyId = historySequence.incrementAndGet();
        Instant startedAt = Instant.now();
        JobExecutionPlan plan;
        try {
            plan = resolvePlan(job, context);
        } catch (Exception ex) {
            Instant failedAt = Instant.now();
            publishEvent(
                    job.id(),
                    new ExecutorEventMessage(
                            "JOB_FINISHED",
                            pipelineId,
                            job.id(),
                            "FAILED",
                            historyId,
                            startedAt,
                            failedAt,
                            Math.max(0L, failedAt.toEpochMilli() - startedAt.toEpochMilli()),
                            String.join(
                                    "\n",
                                    line("ERROR", "Не удалось определить план выполнения задачи."),
                                    line("ERROR", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                            ),
                            additionalData(commandMessage, "failed", "Ошибка построения плана", -1, false)
                    )
            );
            return false;
        }

        publishEvent(
                job.id(),
                new ExecutorEventMessage(
                        "JOB_RUNNING",
                        pipelineId,
                        job.id(),
                        "RUNNING",
                        historyId,
                        startedAt,
                        null,
                        null,
                        String.join(
                                "\n",
                                line("INFO", retry ? "Повторное выполнение начато." : "Выполнение начато."),
                                line("INFO", "Шаг: " + plan.description()),
                                line("DEBUG", "Команда: " + sanitizeCommandForLogs(plan.command()))
                        ),
                        additionalData(commandMessage, "running", plan.description(), null, null)
                )
        );

        CommandResult result = runCommand(plan.command(), plan.workingDirectory());
        if (plan.newProjectDirectory() != null && result.exitCode() == 0) {
            context.setProjectDirectory(plan.newProjectDirectory());
        }

        Instant finishedAt = Instant.now();
        long durationMs = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        String status = resolveFinalStatus(result, pipelineId);
        String logs = String.join(
                "\n",
                line("INFO", "Шаг: " + plan.description()),
                line("INFO", "Завершение: статус=" + status + ", code=" + result.exitCode()),
                result.output()
        );

        publishEvent(
                job.id(),
                new ExecutorEventMessage(
                        "JOB_FINISHED",
                        pipelineId,
                        job.id(),
                        status,
                        historyId,
                        startedAt,
                        finishedAt,
                        durationMs,
                        logs,
                        additionalData(commandMessage, "finished", plan.description(), result.exitCode(), result.timedOut())
                )
        );

        return "SUCCESS".equals(status);
    }

    private JobExecutionPlan resolvePlan(JobResponse job, ExecutionContext context) {
        if (job.script() != null && !job.script().isBlank() && (job.scriptPrimary() || job.params() == null)) {
            return scriptPlan(job.script(), context.projectDirectory(), "Ручной скрипт задачи");
        }

        if (job.params() == null || job.params().jobTemplateId() == null) {
            if (job.script() != null && !job.script().isBlank()) {
                return scriptPlan(job.script(), context.projectDirectory(), "Ручной скрипт задачи");
            }
            throw new IllegalStateException("Не определен шаблон или скрипт для job=" + job.id());
        }

        JobTemplateResponse template = masterApiClient.fetchJobTemplate(job.params().jobTemplateId());
        Map<String, Object> params = job.params().params() == null ? Map.of() : job.params().params();
        return templatePlan(template.path(), params, context);
    }

    private JobExecutionPlan templatePlan(String path, Map<String, Object> params, ExecutionContext context) {
        return switch (path) {
            case "vsc/git" -> gitClonePlan(params, context);
            case "vsc/mercurial" -> mercurialClonePlan(params, context);
            case "build/maven" -> mavenPlan(params, context);
            case "build/gradle" -> gradlePlan(params, context);
            case "build/javac" -> javacPlan(params, context);
            case "build/gcc" -> gccPlan(params, context);
            default -> throw new IllegalStateException("Неподдерживаемый шаблон: " + path);
        };
    }

    private JobExecutionPlan gitClonePlan(Map<String, Object> params, ExecutionContext context) {
        String url = requireString(params, "url", "URL Git-репозитория не задан");
        String branch = value(params, "branch", "");
        String login = value(params, "login", "");
        String password = value(params, "password", "");
        String targetDir = value(params, "targetDir", "source");

        String cloneUrl = enrichUrlWithCredentials(url, login, password);
        Path checkoutDir = safeResolve(context.pipelineDirectory(), targetDir, context.pipelineDirectory());
        cleanupDirectory(checkoutDir, context.pipelineDirectory());

        List<String> command = new ArrayList<>(List.of("git", "clone"));
        if (!branch.isBlank()) {
            command.add("--branch");
            command.add(branch);
        }
        command.add(cloneUrl);
        command.add(checkoutDir.toString());

        return new JobExecutionPlan(
                command,
                context.pipelineDirectory(),
                "Загрузка исходников из Git",
                checkoutDir
        );
    }

    private JobExecutionPlan mercurialClonePlan(Map<String, Object> params, ExecutionContext context) {
        String url = requireString(params, "url", "URL Mercurial-репозитория не задан");
        String branch = value(params, "branch", "");
        String targetDir = value(params, "targetDir", "source");

        Path checkoutDir = safeResolve(context.pipelineDirectory(), targetDir, context.pipelineDirectory());
        cleanupDirectory(checkoutDir, context.pipelineDirectory());

        List<String> command = new ArrayList<>(List.of("hg", "clone"));
        if (!branch.isBlank()) {
            command.add("-b");
            command.add(branch);
        }
        command.add(url);
        command.add(checkoutDir.toString());

        return new JobExecutionPlan(
                command,
                context.pipelineDirectory(),
                "Загрузка исходников из Mercurial",
                checkoutDir
        );
    }

    private JobExecutionPlan mavenPlan(Map<String, Object> params, ExecutionContext context) {
        Path workingDir = resolveWorkingDir(params, context);
        String goals = firstNotBlank(value(params, "goals", ""), value(params, "args", ""), "clean package -DskipTests");
        String executable = detectMavenExecutable(workingDir);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(splitArgs(goals));
        return new JobExecutionPlan(command, workingDir, "Сборка проекта Maven", null);
    }

    private JobExecutionPlan gradlePlan(Map<String, Object> params, ExecutionContext context) {
        Path workingDir = resolveWorkingDir(params, context);
        String tasks = firstNotBlank(value(params, "tasks", ""), value(params, "args", ""), "build");
        boolean useWrapper = Boolean.parseBoolean(value(params, "useWrapper", "true"));
        String executable = detectGradleExecutable(workingDir, useWrapper);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(splitArgs(tasks));
        return new JobExecutionPlan(command, workingDir, "Сборка проекта Gradle", null);
    }

    private JobExecutionPlan javacPlan(Map<String, Object> params, ExecutionContext context) {
        Path workingDir = resolveWorkingDir(params, context);
        String args = firstNotBlank(value(params, "args", ""), "-version");
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.addAll(splitArgs(args));
        return new JobExecutionPlan(command, workingDir, "Сборка Java через javac", null);
    }

    private JobExecutionPlan gccPlan(Map<String, Object> params, ExecutionContext context) {
        Path workingDir = resolveWorkingDir(params, context);
        String args = firstNotBlank(value(params, "args", ""), "--version");
        List<String> command = new ArrayList<>();
        command.add("gcc");
        command.addAll(splitArgs(args));
        return new JobExecutionPlan(command, workingDir, "Сборка C/C++ через gcc", null);
    }

    private JobExecutionPlan scriptPlan(String script, Path workingDir, String description) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("powershell");
            command.add("-NoProfile");
            command.add("-Command");
            command.add(script);
        } else {
            command.add("bash");
            command.add("-lc");
            command.add(script);
        }
        return new JobExecutionPlan(command, workingDir, description, null);
    }

    private CommandResult runCommand(List<String> command, Path workingDirectory) {
        try {
            Files.createDirectories(workingDirectory);
        } catch (IOException ex) {
            return new CommandResult(-1, line("ERROR", "Не удалось подготовить рабочую директорию: " + ex.getMessage()), false);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        StringBuilder output = new StringBuilder();
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException ex) {
            return new CommandResult(-1, line("ERROR", ex.getMessage()), false);
        }

        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // no-op
            }
        });
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(buildServiceProperties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            reader.join(1_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(-1, output.toString(), true);
        }

        int exitCode = finished ? process.exitValue() : -1;
        return new CommandResult(exitCode, output.toString().trim(), !finished);
    }

    private void publishSkipped(JobResponse job, UUID pipelineId, String reason) {
        long historyId = historySequence.incrementAndGet();
        Instant started = Instant.now();
        publishEvent(
                job.id(),
                new ExecutorEventMessage(
                        "JOB_SKIPPED",
                        pipelineId,
                        job.id(),
                        "CANCELED",
                        historyId,
                        started,
                        started,
                        0L,
                        line("WARN", "Задача пропущена: " + reason),
                        Map.of("result", "canceled", "reason", reason)
                )
        );
    }

    private Path resolvePipelineWorkspace(UUID pipelineId) {
        Path root = Path.of(buildServiceProperties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path pipelineDir = root.resolve(pipelineId.toString()).normalize();
        try {
            Files.createDirectories(pipelineDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось создать рабочую директорию: " + pipelineDir, ex);
        }
        return pipelineDir;
    }

    private Path resolveWorkingDir(Map<String, Object> params, ExecutionContext context) {
        String workDirValue = value(params, "workDir", ".");
        return safeResolve(context.projectDirectory(), workDirValue, context.projectDirectory());
    }

    private String resolveFinalStatus(CommandResult result, UUID pipelineId) {
        if (isCanceled(pipelineId)) {
            return "CANCELED";
        }
        if (result.timedOut()) {
            return "FAILED";
        }
        return result.exitCode() == 0 ? "SUCCESS" : "FAILED";
    }

    private Map<String, Object> additionalData(
            ExecutorCommandMessage commandMessage,
            String phase,
            String description,
            Integer exitCode,
            Boolean timedOut
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", phase);
        data.put("commandType", commandMessage.commandType());
        data.put("requestedAt", commandMessage.requestedAt());
        data.put("initiatedBy", commandMessage.initiatedBy());
        if (description != null) {
            data.put("description", description);
        }
        if (exitCode != null) {
            data.put("exitCode", exitCode);
        }
        if (timedOut != null) {
            data.put("timedOut", timedOut);
        }
        return data;
    }

    private String detectMavenExecutable(Path workingDir) {
        if (isWindows() && Files.exists(workingDir.resolve("mvnw.cmd"))) {
            return "mvnw.cmd";
        }
        if (!isWindows() && Files.exists(workingDir.resolve("mvnw"))) {
            return "./mvnw";
        }
        return "mvn";
    }

    private String detectGradleExecutable(Path workingDir, boolean useWrapper) {
        if (useWrapper) {
            if (isWindows() && Files.exists(workingDir.resolve("gradlew.bat"))) {
                return "gradlew.bat";
            }
            if (!isWindows() && Files.exists(workingDir.resolve("gradlew"))) {
                return "./gradlew";
            }
        }
        return "gradle";
    }

    private Path safeResolve(Path base, String child, Path fallback) {
        Path resolved = base.resolve(child).normalize();
        if (!resolved.startsWith(base.normalize())) {
            return fallback;
        }
        return resolved;
    }

    private void cleanupDirectory(Path directory, Path guardRoot) {
        if (!directory.startsWith(guardRoot.normalize())) {
            return;
        }
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // no-op
                        }
                    });
        } catch (IOException ignored) {
            // no-op
        }
    }

    private List<String> splitArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = ARG_PATTERN.matcher(raw);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                result.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                result.add(matcher.group(2));
            } else if (matcher.group(3) != null) {
                result.add(matcher.group(3));
            }
        }
        return result;
    }

    private String enrichUrlWithCredentials(String url, String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            return url;
        }
        if (url.startsWith("https://")) {
            return "https://" + login + ":" + password + "@" + url.substring("https://".length());
        }
        if (url.startsWith("http://")) {
            return "http://" + login + ":" + password + "@" + url.substring("http://".length());
        }
        return url;
    }

    private String sanitizeCommandForLogs(List<String> command) {
        String joined = String.join(" ", command);
        return joined.replaceAll("://[^\\s:@]+:[^\\s@]+@", "://***:***@");
    }

    private String requireString(Map<String, Object> params, String key, String error) {
        String value = value(params, key, "");
        if (value.isBlank()) {
            throw new IllegalStateException(error);
        }
        return value;
    }

    private String value(Map<String, Object> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int safeOrder(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private boolean isCanceled(UUID pipelineId) {
        AtomicBoolean flag = cancellationFlags.get(pipelineId);
        return flag != null && flag.get();
    }

    private String extractCancelReason(ExecutorCommandMessage commandMessage) {
        if (commandMessage.payload() == null) {
            return "manual_cancel";
        }
        Object reason = commandMessage.payload().get("reason");
        return reason == null ? "manual_cancel" : String.valueOf(reason);
    }

    private String resolveCancelReason(UUID pipelineId) {
        return cancellationReasons.getOrDefault(pipelineId, "manual_cancel");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private String line(String level, String message) {
        return Instant.now() + " [" + level + "] " + message;
    }

    private void publishEvent(UUID jobId, ExecutorEventMessage eventMessage) {
        String key = jobId == null ? UUID.randomUUID().toString() : jobId.toString();
        kafkaTemplate.send(kafkaProperties.getTopics().getExecutorEvents(), key, eventMessage);
    }

    @PreDestroy
    void shutdownExecutor() {
        executorPool.shutdownNow();
    }

    private static final class ExecutionContext {
        private final UUID pipelineId;
        private final Path pipelineDirectory;
        private Path projectDirectory;

        private ExecutionContext(UUID pipelineId, Path pipelineDirectory) {
            this.pipelineId = pipelineId;
            this.pipelineDirectory = pipelineDirectory;
            this.projectDirectory = pipelineDirectory;
        }

        private UUID pipelineId() {
            return pipelineId;
        }

        private Path pipelineDirectory() {
            return pipelineDirectory;
        }

        private Path projectDirectory() {
            return projectDirectory;
        }

        private void setProjectDirectory(Path nextProjectDirectory) {
            this.projectDirectory = nextProjectDirectory;
        }
    }

    private record JobExecutionPlan(
            List<String> command,
            Path workingDirectory,
            String description,
            Path newProjectDirectory
    ) {
    }

    private record CommandResult(
            int exitCode,
            String output,
            boolean timedOut
    ) {
    }
}
