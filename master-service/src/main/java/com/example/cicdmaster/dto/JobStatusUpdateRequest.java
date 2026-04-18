package com.example.cicdmaster.dto;

import com.example.cicdmaster.domain.enums.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(
        @NotNull JobStatus status
) {
}
