package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.FolderEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
}
