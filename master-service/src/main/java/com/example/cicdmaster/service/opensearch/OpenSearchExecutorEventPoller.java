package com.example.cicdmaster.service.opensearch;

import com.example.cicdmaster.config.ExecutorEventsProperties;
import com.example.cicdmaster.kafka.message.ExecutorEventMessage;
import com.example.cicdmaster.service.ExecutorEventService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.executor-events", name = "transport", havingValue = "opensearch")
public class OpenSearchExecutorEventPoller {

    private final OpenSearchClient openSearchClient;
    private final ExecutorEventService executorEventService;
    private final ExecutorEventsProperties executorEventsProperties;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private volatile List<String> searchAfter = List.of();
    private Instant lowerBound;

    @PostConstruct
    void initCursor() {
        int lookbackSeconds = Math.max(0, executorEventsProperties.getOpenSearch().getStartupLookbackSeconds());
        lowerBound = Instant.now().minusSeconds(lookbackSeconds);
    }

    @Scheduled(fixedDelayString = "${app.executor-events.opensearch.poll-interval-ms:1000}")
    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try {
            int pagesLeft = Math.max(1, executorEventsProperties.getOpenSearch().getMaxPagesPerPoll());
            while (pagesLeft-- > 0) {
                List<Hit<ExecutorEventDocument>> hits = fetchBatch();
                if (hits.isEmpty()) {
                    return;
                }
                for (Hit<ExecutorEventDocument> hit : hits) {
                    ExecutorEventDocument document = hit.source();
                    if (document != null) {
                        processDocument(document);
                    }
                    if (hit.sort() != null && !hit.sort().isEmpty()) {
                        searchAfter = hit.sort();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("OpenSearch poller iteration failed: {}", ex.getMessage());
            log.debug("OpenSearch poller stacktrace", ex);
        } finally {
            polling.set(false);
        }
    }

    private List<Hit<ExecutorEventDocument>> fetchBatch() throws IOException {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(executorEventsProperties.getOpenSearch().getIndex())
                .size(Math.max(1, executorEventsProperties.getOpenSearch().getBatchSize()))
                .query(query -> query.range(range -> range
                        .field("ingestedAt")
                        .gte(JsonData.of(lowerBound.toString()))))
                .sort(sort -> sort.field(field -> field.field("ingestedAt").order(SortOrder.Asc)))
                .sort(sort -> sort.field(field -> field.field("documentId").order(SortOrder.Asc)));

        if (searchAfter != null && !searchAfter.isEmpty()) {
            requestBuilder.searchAfter(searchAfter);
        }

        SearchResponse<ExecutorEventDocument> response = openSearchClient.search(
                requestBuilder.build(),
                ExecutorEventDocument.class
        );
        return response.hits().hits();
    }

    private void processDocument(ExecutorEventDocument document) {
        if ("JOB_LOG".equalsIgnoreCase(document.eventType())) {
            return;
        }

        UUID jobId = parseUuid(document.jobId());
        if (jobId == null) {
            return;
        }

        ExecutorEventMessage eventMessage = new ExecutorEventMessage(
                blankToNull(document.eventType()),
                parseUuid(document.pipelineId()),
                jobId,
                blankToNull(document.status()),
                document.historyId(),
                parseInstant(document.startedAt()),
                parseInstant(document.finishedAt()),
                document.durationMs(),
                document.logs(),
                document.additionalData()
        );
        executorEventService.handleEvent(eventMessage);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
