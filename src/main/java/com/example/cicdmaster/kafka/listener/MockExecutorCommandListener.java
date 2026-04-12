package com.example.cicdmaster.kafka.listener;

import com.example.cicdmaster.kafka.message.ExecutorCommandMessage;
import com.example.cicdmaster.service.MockExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.mock-executor", name = "enabled", havingValue = "true")
public class MockExecutorCommandListener {

    private final MockExecutorService mockExecutorService;

    @KafkaListener(
            topics = "${app.kafka.topics.executor-commands}",
            containerFactory = "executorCommandKafkaListenerContainerFactory"
    )
    public void onExecutorCommand(ExecutorCommandMessage commandMessage) {
        mockExecutorService.handleCommand(commandMessage);
    }
}
