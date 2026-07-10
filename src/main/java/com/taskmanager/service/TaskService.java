package com.taskmanager.service;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskChangeHistory;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.TaskChangeHistoryRepository;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.MoveTaskRequest;
import com.taskmanager.web.api.dto.PageResponse;
import com.taskmanager.web.api.dto.TaskHistoryEntryResponse;
import com.taskmanager.web.api.dto.TaskRequest;
import com.taskmanager.web.api.dto.TaskResponse;
import com.taskmanager.web.exception.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final ColumnService columnService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskChangeHistoryRepository taskChangeHistoryRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public TaskService(
            TaskRepository taskRepository,
            ColumnService columnService,
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            TaskChangeHistoryRepository taskChangeHistoryRepository,
            CurrentUserService currentUserService,
            UserService userService) {
        this.taskRepository = taskRepository;
        this.columnService = columnService;
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.taskChangeHistoryRepository = taskChangeHistoryRepository;
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
        return new PageResponse<>(
                taskPage.getContent().stream().filter(task -> !task.isDeleted()).map(TaskResponse::from).toList(),
                taskPage.getNumber(),
                taskPage.getSize(),
                taskPage.getTotalElements(),
                taskPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        projectAccessService.requireCanRead(getProject(task), currentUserService.getCurrentUser());
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryEntryResponse> getTaskHistory(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        projectAccessService.requireCanRead(getProject(task), currentUserService.getCurrentUser());
        return taskChangeHistoryRepository.findByTaskOrderByCreatedAtDesc(task).stream()
                .map(TaskHistoryEntryResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse createTask(Long columnId, TaskRequest request) {
        BoardColumn column = columnService.getColumnOrThrow(columnId);
        Project project = column.getBoard().getProject();
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());

        Task task = new Task();
        task.setColumn(column);
        applyTaskFields(task, request, project);
        if (request.status() != null) {
            task.transitionTo(request.status());
        }
        task = taskRepository.save(task);
        recordHistory(task, "CREATED", "Task created in column " + columnId);

        log.info("Task created: id={}, columnId={}", task.getId(), columnId);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, TaskRequest request) {
        Task task = getTaskOrThrow(taskId);
        Project project = getProject(task);
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());

        applyTaskFields(task, request, project);
        task = taskRepository.save(task);
        recordHistory(task, "UPDATED", "Task updated");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse moveTask(Long taskId, MoveTaskRequest request) {
        Task task = getTaskOrThrow(taskId);
        Project project = getProject(task);
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());

        BoardColumn targetColumn = columnService.getColumnOrThrow(request.columnId());
        if (!targetColumn.getBoard().getId().equals(task.getColumn().getBoard().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Task can only be moved within the same board");
        }

        task.transitionTo(TaskStatus.IN_PROGRESS);
        task.setColumn(targetColumn);
        task = taskRepository.save(task);
        recordHistory(task, "MOVED", "Task moved to column " + request.columnId());
        log.info("Task moved: id={}, columnId={}", taskId, request.columnId());
        return TaskResponse.from(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        projectAccessService.requireCanWriteContent(getProject(task), currentUserService.getCurrentUser());
        task.softDelete();
        taskRepository.save(task);
        recordHistory(task, "DELETED", "Task soft deleted");
        log.info("Task deleted: id={}", taskId);
    }

    private void applyTaskFields(Task task, TaskRequest request, Project project) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDeadline(request.deadline());
        task.setAssignee(resolveAssignee(request.assigneeId(), project));
    }

    private void recordHistory(Task task, String action, String details) {
        TaskChangeHistory history = new TaskChangeHistory();
        history.setTask(task);
        history.setChangedBy(currentUserService.getCurrentUser());
        history.setAction(action);
        history.setDetails(details);
        taskChangeHistoryRepository.save(history);
    }

    private User resolveAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userService.getUserById(assigneeId);
        if (!projectMemberRepository.existsByProjectAndUser(project, assignee)) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Assignee must be a project member");
        }
        return assignee;
    }

    private Project getProject(Task task) {
        return task.getColumn().getBoard().getProject();
    }

    private Task getTaskOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Task not found"));
    }
}
