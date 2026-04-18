package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobTemplateEntity;
import com.example.cicdmaster.dto.JobTemplateResponse;
import com.example.cicdmaster.dto.JobTemplateUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.JobTemplateRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobTemplateService {

    private final JobTemplateRepository jobTemplateRepository;

    public List<JobTemplateResponse> findAll() {
        return jobTemplateRepository.findAll().stream().map(this::toResponse).toList();
    }

    public JobTemplateResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public JobTemplateResponse create(JobTemplateUpsertRequest request) {
        JobTemplateEntity entity = new JobTemplateEntity();
        apply(entity, request);
        return toResponse(jobTemplateRepository.save(entity));
    }

    @Transactional
    public JobTemplateResponse update(UUID id, JobTemplateUpsertRequest request) {
        JobTemplateEntity entity = getEntity(id);
        apply(entity, request);
        return toResponse(jobTemplateRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        jobTemplateRepository.delete(getEntity(id));
    }

    public JobTemplateEntity getEntity(UUID id) {
        return jobTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job template not found: " + id));
    }

    private void apply(JobTemplateEntity entity, JobTemplateUpsertRequest request) {
        entity.setPath(request.path());
        entity.setParamsTemplate(request.paramsTemplate());
    }

    private JobTemplateResponse toResponse(JobTemplateEntity entity) {
        return new JobTemplateResponse(entity.getId(), entity.getPath(), entity.getParamsTemplate());
    }
}
