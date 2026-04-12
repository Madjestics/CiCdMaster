package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.TriggerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerRepository extends JpaRepository<TriggerEntity, Long> {

    List<TriggerEntity> findByPipelineId(java.util.UUID pipelineId);
}
