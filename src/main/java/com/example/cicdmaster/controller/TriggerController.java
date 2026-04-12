package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.TriggerResponse;
import com.example.cicdmaster.dto.TriggerUpsertRequest;
import com.example.cicdmaster.service.TriggerService;
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
@RequestMapping("/api/v1/triggers")
public class TriggerController {

    private final TriggerService triggerService;

    @GetMapping("/{id}")
    public TriggerResponse findById(@PathVariable Long id) {
        return triggerService.findById(id);
    }

    @GetMapping("/by-pipeline/{pipelineId}")
    public List<TriggerResponse> findByPipeline(@PathVariable UUID pipelineId) {
        return triggerService.findByPipeline(pipelineId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TriggerResponse create(@Valid @RequestBody TriggerUpsertRequest request) {
        return triggerService.create(request);
    }

    @PutMapping("/{id}")
    public TriggerResponse update(@PathVariable Long id, @Valid @RequestBody TriggerUpsertRequest request) {
        return triggerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        triggerService.delete(id);
    }
}
