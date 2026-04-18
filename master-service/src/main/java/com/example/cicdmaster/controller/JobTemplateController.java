package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.JobTemplateResponse;
import com.example.cicdmaster.dto.JobTemplateUpsertRequest;
import com.example.cicdmaster.service.JobTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/job-templates")
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;

    @GetMapping
    public List<JobTemplateResponse> findAll() {
        return jobTemplateService.findAll();
    }

    @GetMapping("/{id}")
    public JobTemplateResponse findById(@PathVariable UUID id) {
        return jobTemplateService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobTemplateResponse create(@Valid @RequestBody JobTemplateUpsertRequest request) {
        return jobTemplateService.create(request);
    }

    @PutMapping("/{id}")
    public JobTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody JobTemplateUpsertRequest request) {
        return jobTemplateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobTemplateService.delete(id);
    }
}
