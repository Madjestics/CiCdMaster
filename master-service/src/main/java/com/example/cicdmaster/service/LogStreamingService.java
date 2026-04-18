package com.example.cicdmaster.service;

import com.example.cicdmaster.dto.JobLogStreamMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LogStreamingService {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> byJobSubscribers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> allSubscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        if (jobId == null) {
            allSubscribers.add(emitter);
            registerLifecycle(emitter, allSubscribers, null);
            return emitter;
        }

        byJobSubscribers.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        registerLifecycle(emitter, byJobSubscribers.get(jobId), jobId);
        return emitter;
    }

    public void publish(JobLogStreamMessage message) {
        if (message == null) {
            return;
        }

        sendToEmitters(allSubscribers, message);
        if (message.jobId() != null) {
            List<SseEmitter> emitters = byJobSubscribers.get(message.jobId());
            if (emitters != null) {
                sendToEmitters(emitters, message);
            }
        }
    }

    private void sendToEmitters(List<SseEmitter> emitters, JobLogStreamMessage message) {
        emitters.removeIf(emitter -> !trySend(emitter, message));
    }

    private boolean trySend(SseEmitter emitter, JobLogStreamMessage message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("job-log")
                    .id(message.historyId() != null ? String.valueOf(message.historyId()) : "")
                    .data(message));
            return true;
        } catch (IOException | IllegalStateException ex) {
            emitter.complete();
            return false;
        }
    }

    private void registerLifecycle(SseEmitter emitter, List<SseEmitter> container, UUID jobId) {
        emitter.onCompletion(() -> cleanup(container, emitter, jobId));
        emitter.onTimeout(() -> cleanup(container, emitter, jobId));
        emitter.onError(ex -> cleanup(container, emitter, jobId));
    }

    private void cleanup(List<SseEmitter> container, SseEmitter emitter, UUID jobId) {
        container.remove(emitter);
        if (jobId != null && container.isEmpty()) {
            byJobSubscribers.remove(jobId);
        }
    }
}