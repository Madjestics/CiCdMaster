package com.example.cicdmaster.repository;

import com.example.cicdmaster.domain.entity.JobHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobHistoryRepository extends JpaRepository<JobHistoryEntity, Long> {

    List<JobHistoryEntity> findByJobIdOrderByStartDateDesc(UUID jobId);

    @Query("select coalesce(max(history.id), 0) from JobHistoryEntity history")
    long findMaxId();
}
