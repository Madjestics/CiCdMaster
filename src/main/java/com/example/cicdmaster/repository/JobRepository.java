package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.JobEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    List<JobEntity> findByStageIdOrderByOrderIndexAsc(UUID stageId);
}
