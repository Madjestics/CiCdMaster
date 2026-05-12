package com.example.cicdbuild.service.events;

import com.example.cicdbuild.kafka.message.ExecutorEventMessage;
import java.util.UUID;

public interface ExecutorLogPublisher {

    void publish(UUID jobId, ExecutorEventMessage eventMessage);
}
