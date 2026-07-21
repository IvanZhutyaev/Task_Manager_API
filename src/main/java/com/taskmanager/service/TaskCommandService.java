package com.taskmanager.service;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Label;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.ProjectRole;
import com.taskmanager.domain.Sprint;
import com.taskmanager.domain.SprintStatus;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskChangeHistory;
import com.taskmanager.domain.TaskDependency;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.TaskType;
import com.taskmanager.domain.User;
import com.taskmanager.repository.LabelRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.SprintRepository;
import com.taskmanager.repository.TaskChangeHistoryRepository;
import com.taskmanager.repository.TaskChecklistItemRepository;
import com.taskmanager.repository.TaskDependencyRepository;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.MoveTaskRequest;
import com.taskmanager.web.api.dto.TaskRequest;
import com.taskmanager.web.api.dto.TaskResponse;
import com.taskmanager.web.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final TaskChecklistItemRepository checklistItemRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final LabelRepository labelRepository;
    private final SprintRepository sprintRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final TaskQueryService taskQueryService;
    private final BusinessRules businessRules;
    private final NotificationService notificationService;
    private final ProjectActivityService projectActivityService;

    public TaskCommandService(
            TaskRepository taskRepository,
            ColumnService columnService,
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            TaskChangeHistoryRepository taskChangeHistoryRepository,
            TaskChecklistItemRepository checklistItemRepository,
            TaskDependencyRepository dependencyRepository,
            LabelRepository labelRepository,
            SprintRepository sprintRepository,
            CurrentUserService currentUserService,
            UserService userService,
            TaskQueryService taskQueryService,
            BusinessRules businessRules,
            NotificationService notificationService,
            ProjectActivityService projectActivityService) {
        this.taskRepository = taskRepository;
        this.columnService = columnService;
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.taskChangeHistoryRepository = taskChangeHistoryRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.dependencyRepository = dependencyRepository;
        this.labelRepository = labelRepository;
        this.sprintRepository = sprintRepository;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.taskQueryService = taskQueryService;
        this.businessRules = businessRules;
        this.notificationService = notificationService;
        this.projectActivityService = projectActivityService;
    }

    @Transactional
    public TaskResponse createTask(Long columnId, TaskRequest request) {
        BoardColumn column = columnService.getColumnOrThrow(columnId);
        Project project = column.getBoard().getProject();
        User current = currentUserService.getCurrentUser();
        projectAccessService.requireCanWriteContent(project, current);

        enforceWipLimit(column);
        validateStrictCreateUpdate(project, current, request, null);

        Task task = new Task();
        task.setColumn(column);
        applyTaskFields(task, request, project);
        TaskStatus initial = request.status() != null ? request.status() : TaskStatus.BACKLOG;
        task.setStatus(TaskStatus.BACKLOG);
        if (initial == TaskStatus.DONE) {
            enforceDoneRules(project, task, request);
        }
        task.setStatus(initial);
        task = taskRepository.save(task);
        recordHistory(task, "CREATED", "Task created in column " + columnId);
        projectActivityService.record(project, current, "TASK_CREATED", "Task #" + task.getId() + " created");
        if (task.getAssignee() != null) {
            notificationService.notify(task.getAssignee(), "ASSIGNED", "You were assigned to task: " + task.getTitle(),
                    project.getId(), task.getId());
        }
        log.info("Task created: id={}, columnId={}", task.getId(), columnId);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, TaskRequest request) {
        Task task = taskQueryService.getTaskOrThrow(taskId);
        Project project = taskQueryService.getProject(task);
        User current = currentUserService.getCurrentUser();
        projectAccessService.requireCanWriteContent(project, current);

        validateStrictCreateUpdate(project, current, request, task);
        User previousAssignee = task.getAssignee();
        TaskStatus previousStatus = task.getStatus();

        applyTaskFields(task, request, project);
        if (request.status() != null) {
            if (request.status() == TaskStatus.DONE) {
                enforceDoneRules(project, task, request);
            }
            try {
                task.transitionTo(request.status());
            } catch (IllegalStateException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
            }
        }
        task = taskRepository.save(task);
        recordHistory(task, "UPDATED", "Task updated");
        if (task.getAssignee() != null && (previousAssignee == null || !previousAssignee.getId().equals(task.getAssignee().getId()))) {
            notificationService.notify(task.getAssignee(), "ASSIGNED", "You were assigned to task: " + task.getTitle(),
                    project.getId(), task.getId());
        }
        if (previousStatus != task.getStatus()) {
            notificationService.notifyProjectMembers(project, current, "STATUS_CHANGED",
                    "Task \"" + task.getTitle() + "\" status: " + task.getStatus(), project.getId(), task.getId());
        }
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse moveTask(Long taskId, MoveTaskRequest request) {
        Task task = taskQueryService.getTaskOrThrow(taskId);
        Project project = taskQueryService.getProject(task);
        User current = currentUserService.getCurrentUser();
        projectAccessService.requireCanWriteContent(project, current);

        if (task.getStatus() == TaskStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Archived tasks cannot be moved");
        }

        if (businessRules.isStrict(project)) {
            ProjectRole role = projectAccessService.requireMembership(project, current).getRole();
            boolean editorOrAbove = role == ProjectRole.OWNER || role == ProjectRole.EDITOR;
            if (!editorOrAbove && !task.canBeMovedBy(current)) {
                throw new ApiException(HttpStatus.FORBIDDEN.value(), "Only assignee or editors can move this task");
            }
        }

        BoardColumn targetColumn = columnService.getColumnOrThrow(request.columnId());
        if (!targetColumn.getBoard().getId().equals(task.getColumn().getBoard().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Task can only be moved within the same board");
        }

        if (!targetColumn.getId().equals(task.getColumn().getId())) {
            enforceWipLimit(targetColumn);
        }

        TaskStatus targetStatus = targetColumn.getMappedStatus() != null
                ? targetColumn.getMappedStatus()
                : TaskStatus.IN_PROGRESS;

        if (targetStatus == TaskStatus.DONE) {
            enforceDoneRules(project, task, null);
        }

        try {
            task.transitionTo(targetStatus);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        }

        if (businessRules.isStrict(project)
                && targetStatus == TaskStatus.IN_PROGRESS
                && task.getAssignee() == null) {
            task.setAssignee(current);
        }

        task.setColumn(targetColumn);
        task = taskRepository.save(task);
        recordHistory(task, "MOVED", "Task moved to column " + request.columnId());
        log.info("Task moved: id={}, columnId={}", taskId, request.columnId());
        return TaskResponse.from(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = taskQueryService.getTaskOrThrow(taskId);
        Project project = taskQueryService.getProject(task);
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());
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
        task.setEstimateHours(request.estimateHours());
        task.setSpentHours(request.spentHours());
        task.setTaskType(request.taskType());
        task.setAssignee(resolveAssignee(request.assigneeId(), project));
        task.setSprint(resolveSprint(request.sprintId(), project));
        task.setLabels(resolveLabels(request.labelIds(), project));
    }

    private void validateStrictCreateUpdate(Project project, User current, TaskRequest request, Task existing) {
        if (businessRules.isStrict(project)) {
            if (request.deadline() != null && request.deadline().isBefore(LocalDate.now())) {
                ProjectRole role = projectAccessService.requireMembership(project, current).getRole();
                if (role != ProjectRole.OWNER) {
                    throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Only owner can set a past deadline");
                }
            }
            if (request.taskType() == TaskType.BUG && request.priority() != null && request.priority() != com.taskmanager.domain.TaskPriority.HIGH) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Bug tasks must have HIGH priority");
            }
            if (existing != null
                    && existing.getStatus() == TaskStatus.IN_PROGRESS
                    && existing.getAssignee() != null
                    && request.assigneeId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot remove assignee from an in-progress task");
            }
        }
    }

    private void enforceDoneRules(Project project, Task task, TaskRequest request) {
        if (task.getId() != null) {
            List<TaskDependency> deps = dependencyRepository.findByTask(task);
            for (TaskDependency dep : deps) {
                TaskStatus blockerStatus = dep.getBlocker().getStatus();
                if (blockerStatus != TaskStatus.DONE && blockerStatus != TaskStatus.ARCHIVED) {
                    throw new ApiException(HttpStatus.BAD_REQUEST.value(),
                            "Cannot complete task while blocker #" + dep.getBlocker().getId() + " is open");
                }
            }
            if (businessRules.isStrict(project) && checklistItemRepository.countIncomplete(task) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot complete task with incomplete checklist items");
            }
        }

        if (!businessRules.isStrict(project)) {
            return;
        }

        LocalDate deadline = request != null && request.deadline() != null ? request.deadline() : task.getDeadline();
        if (deadline == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot complete task without a deadline");
        }
        if (deadline.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot complete an overdue task");
        }

        BigDecimal spent = request != null && request.spentHours() != null ? request.spentHours() : task.getSpentHours();
        if (spent == null || spent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot complete task without spent hours");
        }
    }

    private void enforceWipLimit(BoardColumn column) {
        if (column.getWipLimit() == null) {
            return;
        }
        long count = taskRepository.countByColumnAndDeletedAtIsNull(column);
        if (count >= column.getWipLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(),
                    "WIP limit reached for column (" + column.getWipLimit() + ")");
        }
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

    private Sprint resolveSprint(Long sprintId, Project project) {
        if (sprintId == null) {
            return null;
        }
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Sprint not found"));
        if (!sprint.getProject().getId().equals(project.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Sprint must belong to the same project");
        }
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot assign task to a closed sprint");
        }
        return sprint;
    }

    private Set<Label> resolveLabels(List<Long> labelIds, Project project) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Label> labels = new HashSet<>();
        for (Long labelId : labelIds) {
            Label label = labelRepository.findByIdAndProject(labelId, project)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST.value(), "Label not found in project"));
            labels.add(label);
        }
        return labels;
    }
}
