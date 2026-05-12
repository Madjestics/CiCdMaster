package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.JobHistoryEntity;
import com.example.cicdmaster.dto.JobHistoryCreateRequest;
import com.example.cicdmaster.dto.JobHistoryResponse;
import com.example.cicdmaster.repository.JobHistoryRepository;
import com.example.cicdmaster.service.opensearch.OpenSearchHistoryLogService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobHistoryService {

    private final JobHistoryRepository jobHistoryRepository;
    private final JobService jobService;
    private final ObjectProvider<OpenSearchHistoryLogService> openSearchHistoryLogServiceProvider;

    public List<JobHistoryResponse> findByJob(UUID jobId) {
        List<JobHistoryEntity> history = jobHistoryRepository.findByJobIdOrderByStartDateDesc(jobId);
        Map<Long, String> logsFromOpenSearch = resolveLogsFromOpenSearch(jobId, history);
        return history.stream()
                .map(entity -> toResponse(entity, logsFromOpenSearch.get(entity.getId())))
                .toList();
    }

    @Transactional
    public JobHistoryResponse create(JobHistoryCreateRequest request) {
        JobHistoryEntity entity = new JobHistoryEntity();
        entity.setId(request.id());
        entity.setJob(jobService.getEntity(request.jobId()));
        entity.setDuration(request.duration());
        entity.setStartDate(request.startDate());
        entity.setAdditionalData(request.additionalData());
        return toResponse(jobHistoryRepository.save(entity));
    }

    private Map<Long, String> resolveLogsFromOpenSearch(UUID jobId, List<JobHistoryEntity> history) {
        OpenSearchHistoryLogService openSearchHistoryLogService = openSearchHistoryLogServiceProvider.getIfAvailable();
        if (openSearchHistoryLogService == null || history == null || history.isEmpty()) {
            return Map.of();
        }
        List<Long> historyIds = history.stream().map(JobHistoryEntity::getId).toList();
        return openSearchHistoryLogService.resolveLatestLogsByHistoryId(jobId, historyIds);
    }

    private JobHistoryResponse toResponse(JobHistoryEntity entity) {
        return toResponse(entity, null);
    }

    private JobHistoryResponse toResponse(JobHistoryEntity entity, String externalLogs) {
        return new JobHistoryResponse(
                entity.getId(),
                entity.getJob().getId(),
                entity.getDuration(),
                entity.getStartDate(),
                externalLogs == null ? "" : externalLogs,
                entity.getAdditionalData()
        );
    }
}
