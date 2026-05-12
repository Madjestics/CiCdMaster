package com.example.cicdmaster.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.executor-events.opensearch",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenSearchClientConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient(ExecutorEventsProperties executorEventsProperties) {
        return RestClient.builder(HttpHost.create(executorEventsProperties.getOpenSearch().getEndpoint())).build();
    }

    @Bean(destroyMethod = "close")
    public OpenSearchTransport openSearchTransport(RestClient restClient, ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport openSearchTransport) {
        return new OpenSearchClient(openSearchTransport);
    }
}
