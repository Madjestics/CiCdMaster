package com.example.cicdmaster.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka")
public class AppKafkaProperties {

    private Topics topics = new Topics();
    private MockExecutor mockExecutor = new MockExecutor();

    @Getter
    @Setter
    public static class Topics {

        private String executorCommands = "cicd.executor.commands";
        private String executorEvents = "cicd.executor.events";
    }

    @Getter
    @Setter
    public static class MockExecutor {

        private boolean enabled = true;
        private long queueDelayMs = 350;
        private long runDelayMs = 1_000;
        private long logChunkDelayMs = 650;
    }
}
