package com.taskmanager.web.api.dto;

import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        TaskPriority priority,
        LocalDate deadline,
        Long assigneeId,
        String assigneeName
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getColumn().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getDeadline(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getName() : null
        );
    }
}
