package com.example.cicdbuild.service.events;

import com.example.cicdbuild.config.AppKafkaProperties;
import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.executor-events",
        name = "transport",
        havingValue = "kafka",
        matchIfMissing = true
)
public class KafkaExecutorEventPublisher implements ExecutorEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppKafkaProperties kafkaProperties;

    @Override
    public void publish(UUID jobId, ExecutorEventMessage eventMessage) {
        String key = jobId == null ? UUID.randomUUID().toString() : jobId.toString();
        kafkaTemplate.send(kafkaProperties.getTopics().getExecutorEvents(), key, withoutLogs(eventMessage));
    }

    private ExecutorEventMessage withoutLogs(ExecutorEventMessage eventMessage) {
        if (eventMessage == null || eventMessage.logs() == null) {
            return eventMessage;
        }
        return new ExecutorEventMessage(
                eventMessage.eventType(),
                eventMessage.pipelineId(),
                eventMessage.jobId(),
                eventMessage.status(),
                eventMessage.historyId(),
                eventMessage.startedAt(),
                eventMessage.finishedAt(),
                eventMessage.durationMs(),
                null,
                eventMessage.additionalData()
        );
    }
}
