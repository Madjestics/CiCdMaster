package com.example.cicdmaster.service;

import com.example.cicdmaster.config.AppKafkaProperties;
import com.example.cicdmaster.domain.entity.JobEntity;
import com.example.cicdmaster.domain.entity.PipelineEntity;
import com.example.cicdmaster.dto.PipelineRunRequest;
import com.example.cicdmaster.kafka.message.ExecutorCommandMessage;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutorCommandService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppKafkaProperties kafkaProperties;

    public void sendPipelineRun(PipelineEntity pipeline, PipelineRunRequest request) {
        ExecutorCommandMessage message = ExecutorCommandMessage.builder()
                .commandType("PIPELINE_RUN")
                .pipelineId(pipeline.getId())
                .initiatedBy(request.initiatedBy())
                .requestedAt(Instant.now())
                .payload(request.parameters())
                .build();
        send(message, pipeline.getId().toString());
    }

    public void sendPipelineCancel(PipelineEntity pipeline, String reason) {
        ExecutorCommandMessage message = ExecutorCommandMessage.builder()
                .commandType("PIPELINE_CANCEL")
                .pipelineId(pipeline.getId())
                .requestedAt(Instant.now())
                .payload(Map.of("reason", reason == null ? "manual_cancel" : reason))
                .build();
        send(message, pipeline.getId().toString());
    }

    public void sendJobRetry(JobEntity job, Map<String, Object> runtimeOverrides) {
        ExecutorCommandMessage message = ExecutorCommandMessage.builder()
                .commandType("JOB_RETRY")
                .pipelineId(job.getStage().getPipeline().getId())
                .stageId(job.getStage().getId())
                .jobId(job.getId())
                .requestedAt(Instant.now())
                .payload(runtimeOverrides)
                .build();
        send(message, job.getId().toString());
    }

    private void send(ExecutorCommandMessage message, String key) {
        kafkaTemplate.send(kafkaProperties.getTopics().getExecutorCommands(), key, message);
    }
}
