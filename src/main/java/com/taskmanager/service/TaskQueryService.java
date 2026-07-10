package com.taskmanager.service;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.User;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.PageResponse;
import com.taskmanager.web.api.dto.TaskResponse;
import com.taskmanager.web.exception.ApiException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final ColumnService columnService;
    private final ProjectAccessService projectAccessService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public TaskQueryService(
            TaskRepository taskRepository,
            ColumnService columnService,
            ProjectAccessService projectAccessService,
            CurrentUserService currentUserService,
            UserService userService) {
        this.taskRepository = taskRepository;
        this.columnService = columnService;
        this.projectAccessService = projectAccessService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listTasks(
            Long columnId,
            Long assigneeId,
            TaskPriority priority,
            TaskStatus status,
            String q,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        BoardColumn column = columnService.getColumnOrThrow(columnId);
        projectAccessService.requireCanRead(column.getBoard().getProject(), currentUserService.getCurrentUser());

        User assignee = null;
        if (assigneeId != null) {
            assignee = userService.getUserById(assigneeId);
        }

        String query = StringUtils.hasText(q) ? q.trim() : null;
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy == null ? "createdAt" : sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Task> taskPage = taskRepository.findFilteredPageable(column, assignee, priority, status, query, pageable);
        List<TaskResponse> content = taskPage.getContent().stream()
                .filter(task -> !task.isDeleted())
                .map(TaskResponse::from)
                .toList();
        return new PageResponse<>(content, taskPage.getNumber(), taskPage.getSize(), taskPage.getTotalElements(), taskPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        projectAccessService.requireCanRead(getProject(task), currentUserService.getCurrentUser());
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public Task getTaskOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Task not found"));
    }

    @Transactional(readOnly = true)
    public Project getProject(Task task) {
        return task.getColumn().getBoard().getProject();
    }
}
