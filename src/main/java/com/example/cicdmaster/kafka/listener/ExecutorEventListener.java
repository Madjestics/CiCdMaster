package com.example.cicdmaster.kafka.listener;

import com.example.cicdmaster.config.AppKafkaProperties;
import com.example.cicdmaster.kafka.message.ExecutorEventMessage;
import com.example.cicdmaster.service.ExecutorEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutorEventListener {

    private final ExecutorEventService executorEventService;
    private final AppKafkaProperties kafkaProperties;

    @KafkaListener(topics = "${app.kafka.topics.executor-events}")
    public void onExecutorEvent(ExecutorEventMessage eventMessage) {
        executorEventService.handleEvent(eventMessage);
    }

    public String eventsTopic() {
        return kafkaProperties.getTopics().getExecutorEvents();
    }
}
