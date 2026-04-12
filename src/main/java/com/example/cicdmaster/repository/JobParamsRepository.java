package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.JobParamsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobParamsRepository extends JpaRepository<JobParamsEntity, UUID> {
}
