package com.example.cicdbuild.kafka.listener;

import com.example.cicdbuild.kafka.message.ExecutorCommandMessage;
import com.example.cicdbuild.service.BuildExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutorCommandListener {

    private final BuildExecutorService buildExecutorService;

    @KafkaListener(
            topics = "${app.kafka.topics.executor-commands}",
            containerFactory = "executorCommandKafkaListenerContainerFactory"
    )
    public void onCommand(ExecutorCommandMessage commandMessage) {
        buildExecutorService.handleCommand(commandMessage);
    }
}
