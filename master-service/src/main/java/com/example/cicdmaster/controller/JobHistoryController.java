package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.JobHistoryCreateRequest;
import com.example.cicdmaster.dto.JobHistoryResponse;
import com.example.cicdmaster.service.JobHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/job-history")
public class JobHistoryController {

    private final JobHistoryService jobHistoryService;

    @GetMapping("/by-job/{jobId}")
    public List<JobHistoryResponse> findByJob(@PathVariable UUID jobId) {
        return jobHistoryService.findByJob(jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobHistoryResponse create(@Valid @RequestBody JobHistoryCreateRequest request) {
        return jobHistoryService.create(request);
    }
}
