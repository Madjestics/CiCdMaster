package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobEntity;
import com.example.cicdmaster.domain.entity.JobHistoryEntity;
import com.example.cicdmaster.domain.enums.JobStatus;
import com.example.cicdmaster.dto.JobLogStreamMessage;
import com.example.cicdmaster.kafka.message.ExecutorEventMessage;
import com.example.cicdmaster.repository.JobHistoryRepository;
import com.example.cicdmaster.repository.JobRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutorEventService {

    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final LogStreamingService logStreamingService;

    @Transactional
    public void handleEvent(ExecutorEventMessage eventMessage) {
        if (eventMessage == null) {
            return;
        }
        if (eventMessage.jobId() == null) {
            publishLogUpdate(eventMessage);
            return;
        }

        Optional<JobEntity> optionalJob = jobRepository.findById(eventMessage.jobId());
        if (optionalJob.isEmpty()) {
            return;
        }

        JobEntity job = optionalJob.get();
        parseStatus(eventMessage.status()).ifPresent(job::setStatus);
        jobRepository.save(job);

        if (eventMessage.historyId() != null && eventMessage.startedAt() != null) {
            upsertHistory(job, eventMessage);
        }

        publishLogUpdate(eventMessage);
    }

    private Optional<JobStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JobStatus.valueOf(status.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void upsertHistory(JobEntity job, ExecutorEventMessage eventMessage) {
        JobHistoryEntity history = jobHistoryRepository.findById(eventMessage.historyId())
                .orElseGet(JobHistoryEntity::new);
        history.setId(eventMessage.historyId());
        history.setJob(job);
        history.setStartDate(toLocalDateTime(eventMessage.startedAt()));
        history.setDuration(resolveDuration(eventMessage));
        history.setAdditionalData(enrichAdditionalData(eventMessage));
        jobHistoryRepository.save(history);
    }

    private Map<String, Object> enrichAdditionalData(ExecutorEventMessage eventMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (eventMessage.additionalData() != null) {
            data.putAll(eventMessage.additionalData());
        }
        if (eventMessage.status() != null && !eventMessage.status().isBlank()) {
            data.put("eventStatus", eventMessage.status());
        }
        if (eventMessage.eventType() != null && !eventMessage.eventType().isBlank()) {
            data.put("eventType", eventMessage.eventType());
        }
        return data.isEmpty() ? null : data;
    }

    private long resolveDuration(ExecutorEventMessage eventMessage) {
        if (eventMessage.durationMs() != null) {
            return eventMessage.durationMs();
        }
        if (eventMessage.startedAt() != null && eventMessage.finishedAt() != null) {
            return Math.max(0, eventMessage.finishedAt().toEpochMilli() - eventMessage.startedAt().toEpochMilli());
        }
        return 0L;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private void publishLogUpdate(ExecutorEventMessage eventMessage) {
        JobLogStreamMessage message = new JobLogStreamMessage(
                eventMessage.jobId(),
                eventMessage.pipelineId(),
                eventMessage.eventType(),
                eventMessage.status(),
                eventMessage.historyId(),
                eventMessage.logs(),
                Instant.now(),
                eventMessage.additionalData()
        );
        logStreamingService.publish(message);
    }
}
