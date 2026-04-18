package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.TriggerEntity;
import com.example.cicdmaster.dto.TriggerResponse;
import com.example.cicdmaster.dto.TriggerUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.TriggerRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TriggerService {

    private final TriggerRepository triggerRepository;
    private final PipelineService pipelineService;

    public List<TriggerResponse> findByPipeline(UUID pipelineId) {
        return triggerRepository.findByPipelineId(pipelineId).stream().map(this::toResponse).toList();
    }

    public TriggerResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public TriggerResponse create(TriggerUpsertRequest request) {
        TriggerEntity entity = new TriggerEntity();
        apply(entity, request);
        return toResponse(triggerRepository.save(entity));
    }

    @Transactional
    public TriggerResponse update(Long id, TriggerUpsertRequest request) {
        TriggerEntity entity = getEntity(id);
        apply(entity, request);
        return toResponse(triggerRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        triggerRepository.delete(getEntity(id));
    }

    private TriggerEntity getEntity(Long id) {
        return triggerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trigger not found: " + id));
    }

    private void apply(TriggerEntity entity, TriggerUpsertRequest request) {
        entity.setId(request.id());
        entity.setName(request.name());
        entity.setPipeline(pipelineService.getEntity(request.pipelineId()));
    }

    private TriggerResponse toResponse(TriggerEntity entity) {
        return new TriggerResponse(entity.getId(), entity.getName(), entity.getPipeline().getId());
    }
}
