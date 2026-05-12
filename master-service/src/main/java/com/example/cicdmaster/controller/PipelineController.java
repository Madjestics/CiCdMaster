package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.PipelineCancelRequest;
import com.example.cicdmaster.dto.PipelineRunResponse;
import com.example.cicdmaster.dto.PipelineResponse;
import com.example.cicdmaster.dto.PipelineRunRequest;
import com.example.cicdmaster.dto.PipelineUpsertRequest;
import com.example.cicdmaster.service.PipelineService;
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
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

    private final PipelineService pipelineService;

    @GetMapping
    public List<PipelineResponse> findAll() {
        return pipelineService.findAll();
    }

    @GetMapping("/root")
    public List<PipelineResponse> findRoot() {
        return pipelineService.findRoot();
    }

    @GetMapping("/{id}")
    public PipelineResponse findById(@PathVariable UUID id) {
        return pipelineService.findById(id);
    }

    @GetMapping("/by-folder/{folderId}")
    public List<PipelineResponse> findByFolder(@PathVariable UUID folderId) {
        return pipelineService.findByFolder(folderId);
    }

    @GetMapping("/{id}/runs")
    public List<PipelineRunResponse> findRuns(@PathVariable UUID id) {
        return pipelineService.findRuns(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineResponse create(@Valid @RequestBody PipelineUpsertRequest request) {
        return pipelineService.create(request);
    }

    @PutMapping("/{id}")
    public PipelineResponse update(@PathVariable UUID id, @Valid @RequestBody PipelineUpsertRequest request) {
        return pipelineService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        pipelineService.delete(id);
    }

    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void run(@PathVariable UUID id, @Valid @RequestBody PipelineRunRequest request) {
        pipelineService.run(id, request);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancel(@PathVariable UUID id, @RequestBody(required = false) PipelineCancelRequest request) {
        pipelineService.cancel(id, request);
    }
}
