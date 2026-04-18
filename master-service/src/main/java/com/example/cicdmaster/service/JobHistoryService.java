package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobHistoryEntity;
import com.example.cicdmaster.dto.JobHistoryCreateRequest;
import com.example.cicdmaster.dto.JobHistoryResponse;
import com.example.cicdmaster.repository.JobHistoryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobHistoryService {

    private final JobHistoryRepository jobHistoryRepository;
    private final JobService jobService;

    public List<JobHistoryResponse> findByJob(UUID jobId) {
        return jobHistoryRepository.findByJobIdOrderByStartDateDesc(jobId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public JobHistoryResponse create(JobHistoryCreateRequest request) {
        JobHistoryEntity entity = new JobHistoryEntity();
        entity.setId(request.id());
        entity.setJob(jobService.getEntity(request.jobId()));
        entity.setDuration(request.duration());
        entity.setStartDate(request.startDate());
        entity.setLogs(request.logs());
        entity.setAdditionalData(request.additionalData());
        return toResponse(jobHistoryRepository.save(entity));
    }

    private JobHistoryResponse toResponse(JobHistoryEntity entity) {
        return new JobHistoryResponse(
                entity.getId(),
                entity.getJob().getId(),
                entity.getDuration(),
                entity.getStartDate(),
                entity.getLogs(),
                entity.getAdditionalData()
        );
    }
}
