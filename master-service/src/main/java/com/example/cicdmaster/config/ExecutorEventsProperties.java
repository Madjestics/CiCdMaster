package com.example.cicdmaster.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.executor-events")
public class ExecutorEventsProperties {

    private String transport = "kafka";
    private OpenSearch openSearch = new OpenSearch();

    @Getter
    @Setter
    public static class OpenSearch {

        private boolean enabled = true;
        private String endpoint = "http://localhost:9200";
        private String index = "cicd-executor-events";
        private int batchSize = 200;
        private long pollIntervalMs = 1_000L;
        private int startupLookbackSeconds = 10;
        private int maxPagesPerPoll = 10;
        private int historyFetchSize = 500;
        private int historyMaxPages = 10;
    }
}
