package com.example.cicdmaster.service;

import com.example.cicdmaster.domain.entity.FolderEntity;
import com.example.cicdmaster.dto.FolderResponse;
import com.example.cicdmaster.dto.FolderUpsertRequest;
import com.example.cicdmaster.exception.ResourceNotFoundException;
import com.example.cicdmaster.repository.FolderRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;

    public List<FolderResponse> findAll() {
        return folderRepository.findAll().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .map(this::toResponse)
                .toList();
    }

    public List<FolderResponse> findRoot() {
        return folderRepository.findByParentIsNullOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public List<FolderResponse> findByParent(UUID parentId) {
        return folderRepository.findByParentIdOrderByNameAsc(parentId).stream().map(this::toResponse).toList();
    }

    public FolderResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public FolderResponse create(FolderUpsertRequest request) {
        FolderEntity entity = new FolderEntity();
        apply(entity, request);
        return toResponse(folderRepository.save(entity));
    }

    @Transactional
    public FolderResponse update(UUID id, FolderUpsertRequest request) {
        FolderEntity entity = getEntity(id);
        apply(entity, request);
        return toResponse(folderRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        folderRepository.delete(getEntity(id));
    }

    public FolderEntity getEntity(UUID id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + id));
    }

    private void apply(FolderEntity entity, FolderUpsertRequest request) {
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setParent(request.parentId() == null ? null : getEntity(request.parentId()));
    }

    private FolderResponse toResponse(FolderEntity entity) {
        return new FolderResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getParent() != null ? entity.getParent().getId() : null
        );
    }
}
