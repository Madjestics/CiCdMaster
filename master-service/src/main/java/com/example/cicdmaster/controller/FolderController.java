package com.example.cicdmaster.controller;

import com.example.cicdmaster.dto.FolderResponse;
import com.example.cicdmaster.dto.FolderUpsertRequest;
import com.example.cicdmaster.service.FolderService;
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
@RequestMapping("/api/v1/folders")
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public List<FolderResponse> findAll() {
        return folderService.findAll();
    }

    @GetMapping("/root")
    public List<FolderResponse> findRoot() {
        return folderService.findRoot();
    }

    @GetMapping("/by-parent/{parentId}")
    public List<FolderResponse> findByParent(@PathVariable UUID parentId) {
        return folderService.findByParent(parentId);
    }

    @GetMapping("/{id}")
    public FolderResponse findById(@PathVariable UUID id) {
        return folderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse create(@Valid @RequestBody FolderUpsertRequest request) {
        return folderService.create(request);
    }

    @PutMapping("/{id}")
    public FolderResponse update(@PathVariable UUID id, @Valid @RequestBody FolderUpsertRequest request) {
        return folderService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        folderService.delete(id);
    }
}
