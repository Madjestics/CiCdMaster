package com.example.cicdbuild.service.events;

import com.example.cicdbuild.config.ExecutorEventsProperties;
import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.executor-events", name = "transport", havingValue = "opensearch")
public class OpenSearchExecutorEventPublisher implements ExecutorEventPublisher {

    private final OpenSearchClient openSearchClient;
    private final ExecutorEventsProperties executorEventsProperties;

    @PostConstruct
    void ensureIndexExists() {
        String index = executorEventsProperties.getOpenSearch().getIndex();
        try {
            boolean exists = openSearchClient.indices().exists(request -> request.index(index)).value();
            if (!exists) {
                openSearchClient.indices().create(request -> request
                        .index(index)
                        .mappings(mapping -> mapping
                                .properties("documentId", property -> property.keyword(keyword -> keyword))
                                .properties("ingestedAt", property -> property.date(date -> date))
                                .properties("sourceService", property -> property.keyword(keyword -> keyword))
                                .properties("eventType", property -> property.keyword(keyword -> keyword))
                                .properties("pipelineId", property -> property.keyword(keyword -> keyword))
                                .properties("jobId", property -> property.keyword(keyword -> keyword))
                                .properties("status", property -> property.keyword(keyword -> keyword))
                                .properties("historyId", property -> property.long_(number -> number))
                                .properties("startedAt", property -> property.date(date -> date))
                                .properties("finishedAt", property -> property.date(date -> date))
                                .properties("durationMs", property -> property.long_(number -> number))
                                .properties("logs", property -> property.text(text -> text))
                                .properties("additionalData", property -> property.object(object -> object.enabled(true)))));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize OpenSearch index: " + index, ex);
        }
    }

    @Override
    public void publish(UUID jobId, ExecutorEventMessage eventMessage) {
        if (eventMessage == null) {
            return;
        }
        String index = executorEventsProperties.getOpenSearch().getIndex();
        ExecutorEventDocument document = ExecutorEventDocument.from(withoutLogs(eventMessage), jobId);
        try {
            openSearchClient.index(request -> request
                    .index(index)
                    .id(document.documentId())
                    .document(document));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to publish executor event to OpenSearch", ex);
        }
    }

    private ExecutorEventMessage withoutLogs(ExecutorEventMessage eventMessage) {
        if (eventMessage.logs() == null) {
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
