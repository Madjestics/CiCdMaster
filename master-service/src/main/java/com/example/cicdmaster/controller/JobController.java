package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.JobResponse;
import com.example.cicdmaster.dto.JobRetryRequest;
import com.example.cicdmaster.dto.JobStatusUpdateRequest;
import com.example.cicdmaster.dto.JobUpsertRequest;
import com.example.cicdmaster.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    @GetMapping("/{id}")
    public JobResponse findById(@PathVariable UUID id) {
        return jobService.findById(id);
    }

    @GetMapping("/by-stage/{stageId}")
    public List<JobResponse> findByStage(@PathVariable UUID stageId) {
        return jobService.findByStage(stageId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody JobUpsertRequest request) {
        return jobService.create(request);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable UUID id, @Valid @RequestBody JobUpsertRequest request) {
        return jobService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public JobResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody JobStatusUpdateRequest request) {
        return jobService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@PathVariable UUID id, @RequestBody(required = false) JobRetryRequest request) {
        jobService.retry(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobService.delete(id);
    }
}
