package com.taskmanager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean strictBusinessRules,
        Boolean withDefaultBoard
) {
    public boolean strictBusinessRulesOrDefault() {
        return Boolean.TRUE.equals(strictBusinessRules);
    }

    public boolean withDefaultBoardOrDefault() {
        return Boolean.TRUE.equals(withDefaultBoard);
    }
}
