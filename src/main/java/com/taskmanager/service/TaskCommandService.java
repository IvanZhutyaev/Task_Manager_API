package com.taskmanager.service;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskChangeHistory;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.TaskChangeHistoryRepository;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.MoveTaskRequest;
import com.taskmanager.web.api.dto.TaskRequest;
import com.taskmanager.web.api.dto.TaskResponse;
import com.taskmanager.web.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommandService {

    private static final Logger log = LoggerFactory.getLogger(TaskCommandService.class);

    private final TaskRepository taskRepository;
    private final ColumnService columnService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskChangeHistoryRepository taskChangeHistoryRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final TaskQueryService taskQueryService;

    public TaskCommandService(
            TaskRepository taskRepository,
            ColumnService columnService,
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            TaskChangeHistoryRepository taskChangeHistoryRepository,
            CurrentUserService currentUserService,
            UserService userService,
            TaskQueryService taskQueryService) {
        this.taskRepository = taskRepository;
        this.columnService = columnService;
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.taskChangeHistoryRepository = taskChangeHistoryRepository;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.taskQueryService = taskQueryService;
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
        Task task = taskQueryService.getTaskOrThrow(taskId);
        Project project = taskQueryService.getProject(task);
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());

        applyTaskFields(task, request, project);
        task = taskRepository.save(task);
        recordHistory(task, "UPDATED", "Task updated");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse moveTask(Long taskId, MoveTaskRequest request) {
        Task task = taskQueryService.getTaskOrThrow(taskId);
        Project project = taskQueryService.getProject(task);
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
        Task task = taskQueryService.getTaskOrThrow(taskId);
        projectAccessService.requireCanWriteContent(taskQueryService.getProject(task), currentUserService.getCurrentUser());
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
}
