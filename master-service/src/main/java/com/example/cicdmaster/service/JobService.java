package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobEntity;
import com.example.cicdmaster.domain.entity.JobParamsEntity;
import com.example.cicdmaster.domain.entity.JobTemplateEntity;
import com.example.cicdmaster.domain.enums.JobStatus;
import com.example.cicdmaster.dto.JobParamsView;
import com.example.cicdmaster.dto.JobResponse;
import com.example.cicdmaster.dto.JobRetryRequest;
import com.example.cicdmaster.dto.JobUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.JobParamsRepository;
import com.example.cicdmaster.repository.JobRepository;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobParamsRepository jobParamsRepository;
    private final StageService stageService;
    private final JobTemplateService jobTemplateService;
    private final ExecutorCommandService executorCommandService;

    public List<JobResponse> findByStage(UUID stageId) {
        return jobRepository.findByStageIdOrderByOrderIndexAsc(stageId).stream().map(this::toResponse).toList();
    }

    public JobResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public JobResponse create(JobUpsertRequest request) {
        JobEntity entity = new JobEntity();
        apply(entity, request);
        JobEntity saved = jobRepository.save(entity);
        syncParams(saved, request);
        return toResponse(saved);
    }

    @Transactional
    public JobResponse update(UUID id, JobUpsertRequest request) {
        JobEntity entity = getEntity(id);
        apply(entity, request);
        JobEntity saved = jobRepository.save(entity);
        syncParams(saved, request);
        return toResponse(saved);
    }

    @Transactional
    public JobResponse updateStatus(UUID id, JobStatus status) {
        JobEntity entity = getEntity(id);
        entity.setStatus(status);
        return toResponse(jobRepository.save(entity));
    }

    @Transactional
    public void retry(UUID id, JobRetryRequest request) {
        JobEntity job = getEntity(id);
        job.setStatus(JobStatus.QUEUED);
        jobRepository.save(job);
        executorCommandService.sendJobRetry(job, request != null ? request.runtimeOverrides() : null);
    }

    @Transactional
    public void delete(UUID id) {
        getEntity(id);
        jobParamsRepository.findById(id).ifPresent(jobParamsRepository::delete);
        jobRepository.deleteById(id);
    }

    public JobEntity getEntity(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    private void apply(JobEntity entity, JobUpsertRequest request) {
        entity.setStage(stageService.getEntity(request.stageId()));
        entity.setOrderIndex(request.order());
        entity.setStatus(request.status());
        entity.setScript(request.script());
        entity.setScriptPrimary(request.scriptPrimary());
    }

    private void syncParams(JobEntity job, JobUpsertRequest request) {
        if (request.jobTemplateId() == null) {
            jobParamsRepository.findById(job.getId()).ifPresent(jobParamsRepository::delete);
            return;
        }

        JobTemplateEntity template = jobTemplateService.getEntity(request.jobTemplateId());
        JobParamsEntity paramsEntity = jobParamsRepository.findById(job.getId()).orElseGet(JobParamsEntity::new);
        paramsEntity.setJob(job);
        paramsEntity.setJobTemplate(template);
        paramsEntity.setParams(request.params() == null ? Collections.emptyMap() : request.params());
        jobParamsRepository.save(paramsEntity);
    }

    private JobResponse toResponse(JobEntity entity) {
        JobParamsView paramsView = jobParamsRepository.findById(entity.getId())
                .map(params -> new JobParamsView(params.getJobTemplate().getId(), params.getParams()))
                .orElse(null);

        return new JobResponse(
                entity.getId(),
                entity.getStage().getId(),
                entity.getOrderIndex(),
                entity.getStatus(),
                entity.getScript(),
                entity.isScriptPrimary(),
                paramsView
        );
    }
}
