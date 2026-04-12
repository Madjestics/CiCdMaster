package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.PipelineEntity;
import com.example.cicdmaster.dto.PipelineCancelRequest;
import com.example.cicdmaster.dto.PipelineResponse;
import com.example.cicdmaster.dto.PipelineRunRequest;
import com.example.cicdmaster.dto.PipelineUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.PipelineRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final FolderService folderService;
    private final ExecutorCommandService executorCommandService;

    public List<PipelineResponse> findAll() {
        return pipelineRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PipelineResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    public List<PipelineResponse> findByFolder(UUID folderId) {
        return pipelineRepository.findByFolderId(folderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PipelineResponse create(PipelineUpsertRequest request) {
        PipelineEntity entity = new PipelineEntity();
        apply(entity, request);
        return toResponse(pipelineRepository.save(entity));
    }

    @Transactional
    public PipelineResponse update(UUID id, PipelineUpsertRequest request) {
        PipelineEntity entity = getEntity(id);
        apply(entity, request);
        return toResponse(pipelineRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        pipelineRepository.delete(getEntity(id));
    }

    public void run(UUID id, PipelineRunRequest request) {
        executorCommandService.sendPipelineRun(getEntity(id), request);
    }

    public void cancel(UUID id, PipelineCancelRequest request) {
        executorCommandService.sendPipelineCancel(getEntity(id), request != null ? request.reason() : null);
    }

    public PipelineEntity getEntity(UUID id) {
        return pipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));
    }

    private void apply(PipelineEntity entity, PipelineUpsertRequest request) {
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setFolder(request.folderId() == null ? null : folderService.getEntity(request.folderId()));
    }

    private PipelineResponse toResponse(PipelineEntity entity) {
        return new PipelineResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getFolder() != null ? entity.getFolder().getId() : null
        );
    }
}
