package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.PipelineEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    List<PipelineEntity> findByFolderIdOrderByNameAsc(UUID folderId);

    List<PipelineEntity> findByFolderIsNullOrderByNameAsc();
}
