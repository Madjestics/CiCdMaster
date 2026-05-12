package com.example.cicdbuild.service.events;

import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.executor-events.opensearch", name = "enabled", havingValue = "false")
public class NoopExecutorLogPublisher implements ExecutorLogPublisher {

    @Override
    public void publish(UUID jobId, ExecutorEventMessage eventMessage) {
        // Logs are intentionally not sent to Kafka or the master database.
    }
}
