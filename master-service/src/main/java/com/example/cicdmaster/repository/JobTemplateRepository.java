package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.JobTemplateEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTemplateRepository extends JpaRepository<JobTemplateEntity, UUID> {

    Optional<JobTemplateEntity> findByPath(String path);
}
