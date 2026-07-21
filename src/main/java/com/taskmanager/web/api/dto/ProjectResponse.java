package com.taskmanager.web.api.dto;

import com.taskmanager.domain.Project;
import com.taskmanager.domain.ProjectRole;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerName,
        ProjectRole currentUserRole,
        boolean strictBusinessRules
) {

    public static ProjectResponse from(Project project, ProjectRole currentUserRole) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getOwner().getName(),
                currentUserRole,
                project.isStrictBusinessRules()
        );
    }
}
