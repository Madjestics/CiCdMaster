package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.StageResponse;
import com.example.cicdmaster.dto.StageUpsertRequest;
import com.example.cicdmaster.service.StageService;
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
@RequestMapping("/api/v1/stages")
public class StageController {

    private final StageService stageService;

    @GetMapping("/{id}")
    public StageResponse findById(@PathVariable UUID id) {
        return stageService.findById(id);
    }

    @GetMapping("/by-pipeline/{pipelineId}")
    public List<StageResponse> findByPipeline(@PathVariable UUID pipelineId) {
        return stageService.findByPipeline(pipelineId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StageResponse create(@Valid @RequestBody StageUpsertRequest request) {
        return stageService.create(request);
    }

    @PutMapping("/{id}")
    public StageResponse update(@PathVariable UUID id, @Valid @RequestBody StageUpsertRequest request) {
        return stageService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        stageService.delete(id);
    }
}
