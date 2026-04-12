package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.StageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageRepository extends JpaRepository<StageEntity, UUID> {

    List<StageEntity> findByPipelineIdOrderByOrderIndexAsc(UUID pipelineId);
}
