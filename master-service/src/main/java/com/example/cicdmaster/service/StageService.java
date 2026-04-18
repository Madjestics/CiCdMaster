package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.StageEntity;
import com.example.cicdmaster.dto.StageResponse;
import com.example.cicdmaster.dto.StageUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.StageRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StageService {

    private final StageRepository stageRepository;
    private final PipelineService pipelineService;

    public List<StageResponse> findByPipeline(UUID pipelineId) {
        return stageRepository.findByPipelineIdOrderByOrderIndexAsc(pipelineId).stream()
                .map(this::toResponse)
                .toList();
    }

    public StageResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public StageResponse create(StageUpsertRequest request) {
        StageEntity entity = new StageEntity();
        apply(entity, request);
        return toResponse(stageRepository.save(entity));
    }

    @Transactional
    public StageResponse update(UUID id, StageUpsertRequest request) {
        StageEntity entity = getEntity(id);
        apply(entity, request);
        return toResponse(stageRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        stageRepository.delete(getEntity(id));
    }

    public StageEntity getEntity(UUID id) {
        return stageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stage not found: " + id));
    }

    private void apply(StageEntity entity, StageUpsertRequest request) {
        entity.setPipeline(pipelineService.getEntity(request.pipelineId()));
        entity.setOrderIndex(request.order());
        entity.setName(request.name());
        entity.setDescription(request.description());
    }

    private StageResponse toResponse(StageEntity entity) {
        return new StageResponse(
                entity.getId(),
                entity.getPipeline().getId(),
                entity.getOrderIndex(),
                entity.getName(),
                entity.getDescription()
        );
    }
}
