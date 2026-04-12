package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.JobParamsEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobParamsRepository extends JpaRepository<JobParamsEntity, UUID> {

    @Query("select jobParams.jobTemplate.path from JobParamsEntity jobParams where jobParams.id = :jobId")
    Optional<String> findTemplatePathByJobId(@Param("jobId") UUID jobId);
}
