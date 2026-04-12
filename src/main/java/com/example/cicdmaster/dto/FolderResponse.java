package com.example.cicdmaster.dto;

import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        String description,
        UUID parentId
) {
}
