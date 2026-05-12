package com.example.cicdmaster.service.opensearch;

import com.example.cicdmaster.config.ExecutorEventsProperties;
import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.executor-events.opensearch",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenSearchHistoryLogService {

    private final OpenSearchClient openSearchClient;
    private final ExecutorEventsProperties executorEventsProperties;

    public Map<Long, String> resolveLatestLogsByHistoryId(UUID jobId, Collection<Long> historyIds) {
        if (jobId == null || historyIds == null || historyIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> required = historyIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (required.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> chunksByHistory = new HashMap<>();
        List<String> searchAfter = List.of();
        int pagesLeft = Math.max(1, executorEventsProperties.getOpenSearch().getHistoryMaxPages());

        while (pagesLeft-- > 0) {
            SearchResponse<ExecutorEventDocument> response;
            try {
                response = openSearchClient.search(buildSearchRequest(jobId, searchAfter), ExecutorEventDocument.class);
            } catch (IOException ex) {
                log.warn("Failed to load job history logs from OpenSearch for job {}: {}", jobId, ex.getMessage());
                log.debug("OpenSearch history lookup stacktrace", ex);
                break;
            }

            List<Hit<ExecutorEventDocument>> hits = response.hits().hits();
            if (hits == null || hits.isEmpty()) {
                break;
            }

            for (Hit<ExecutorEventDocument> hit : hits) {
                ExecutorEventDocument document = hit.source();
                if (document == null || document.historyId() == null || !required.contains(document.historyId())) {
                    continue;
                }
                String logs = document.logs();
                if (logs == null || logs.isBlank()) {
                    continue;
                }
                chunksByHistory.computeIfAbsent(document.historyId(), ignored -> new ArrayList<>()).add(logs);
            }

            Hit<ExecutorEventDocument> last = hits.get(hits.size() - 1);
            if (last.sort() == null || last.sort().isEmpty()) {
                break;
            }
            searchAfter = last.sort();
        }

        Map<Long, String> logsByHistory = new HashMap<>();
        chunksByHistory.forEach((historyId, chunks) -> logsByHistory.put(historyId, String.join("\n", chunks)));
        return logsByHistory;
    }

    private SearchRequest buildSearchRequest(UUID jobId, List<String> searchAfter) {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(executorEventsProperties.getOpenSearch().getIndex())
                .size(Math.max(1, executorEventsProperties.getOpenSearch().getHistoryFetchSize()))
                .query(query -> query.bool(bool -> bool
                        .filter(filter -> filter.term(term -> term.field("jobId").value(FieldValue.of(jobId.toString()))))
                        .filter(filter -> filter.term(term -> term.field("eventType").value(FieldValue.of("JOB_LOG"))))
                        .filter(filter -> filter.exists(exists -> exists.field("historyId")))
                        .filter(filter -> filter.exists(exists -> exists.field("logs")))))
                .sort(sort -> sort.field(field -> field.field("ingestedAt").order(SortOrder.Asc)))
                .sort(sort -> sort.field(field -> field.field("documentId").order(SortOrder.Asc)));

        if (searchAfter != null && !searchAfter.isEmpty()) {
            requestBuilder.searchAfter(searchAfter);
        }

        return requestBuilder.build();
    }
}
