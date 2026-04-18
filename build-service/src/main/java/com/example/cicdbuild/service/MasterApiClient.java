package com.example.cicdbuild.service;

import com.example.cicdbuild.config.BuildServiceProperties;
import com.example.cicdbuild.dto.JobResponse;
import com.example.cicdbuild.dto.JobTemplateResponse;
import com.example.cicdbuild.dto.StageResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MasterApiClient {

    private final BuildServiceProperties buildServiceProperties;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(buildServiceProperties.getMasterBaseUrl())
                .build();
    }

    public List<StageResponse> fetchStagesByPipeline(UUID pipelineId) {
        StageResponse[] payload = restClient()
                .get()
                .uri("/api/v1/stages/by-pipeline/{pipelineId}", pipelineId)
                .retrieve()
                .body(StageResponse[].class);
        return payload == null ? Collections.emptyList() : Arrays.asList(payload);
    }

    public List<JobResponse> fetchJobsByStage(UUID stageId) {
        JobResponse[] payload = restClient()
                .get()
                .uri("/api/v1/jobs/by-stage/{stageId}", stageId)
                .retrieve()
                .body(JobResponse[].class);
        return payload == null ? Collections.emptyList() : Arrays.asList(payload);
    }

    public JobResponse fetchJob(UUID jobId) {
        return restClient()
                .get()
                .uri("/api/v1/jobs/{jobId}", jobId)
                .retrieve()
                .body(JobResponse.class);
    }

    public JobTemplateResponse fetchJobTemplate(UUID jobTemplateId) {
        return restClient()
                .get()
                .uri("/api/v1/job-templates/{templateId}", jobTemplateId)
                .retrieve()
                .body(JobTemplateResponse.class);
    }
}
