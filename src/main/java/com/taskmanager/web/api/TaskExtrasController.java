package com.taskmanager.web.api;

import com.taskmanager.config.ApiConstants;
import com.taskmanager.service.TaskChecklistService;
import com.taskmanager.service.TaskCommentService;
import com.taskmanager.service.TaskDependencyService;
import com.taskmanager.web.api.dto.ChecklistItemRequest;
import com.taskmanager.web.api.dto.ChecklistItemResponse;
import com.taskmanager.web.api.dto.CommentRequest;
import com.taskmanager.web.api.dto.CommentResponse;
import com.taskmanager.web.api.dto.DependencyRequest;
import com.taskmanager.web.api.dto.DependencyResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping(value = ApiConstants.API_V1 + "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskExtrasController {

    private final TaskCommentService commentService;
    private final TaskChecklistService checklistService;
    private final TaskDependencyService dependencyService;

    public TaskExtrasController(
            TaskCommentService commentService,
            TaskChecklistService checklistService,
            TaskDependencyService dependencyService) {
        this.commentService = commentService;
        this.checklistService = checklistService;
        this.dependencyService = dependencyService;
    }

    @GetMapping("/{taskId}/comments")
    public List<CommentResponse> listComments(@PathVariable Long taskId) {
        return commentService.list(taskId);
    }

    @PostMapping("/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
            @PathVariable Long taskId,
            @Valid @RequestBody CommentRequest request) {
        return commentService.create(taskId, request);
    }

    @PutMapping("/comments/{commentId}")
    public CommentResponse updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        return commentService.update(commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        commentService.delete(commentId);
    }

    @GetMapping("/{taskId}/checklist")
    public List<ChecklistItemResponse> listChecklist(@PathVariable Long taskId) {
        return checklistService.list(taskId);
    }

    @PostMapping("/{taskId}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItemResponse createChecklistItem(
            @PathVariable Long taskId,
            @Valid @RequestBody ChecklistItemRequest request) {
        return checklistService.create(taskId, request);
    }

    @PutMapping("/checklist/{itemId}")
    public ChecklistItemResponse updateChecklistItem(
            @PathVariable Long itemId,
            @Valid @RequestBody ChecklistItemRequest request) {
        return checklistService.update(itemId, request);
    }

    @DeleteMapping("/checklist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChecklistItem(@PathVariable Long itemId) {
        checklistService.delete(itemId);
    }

    @GetMapping("/{taskId}/dependencies")
    public List<DependencyResponse> listDependencies(@PathVariable Long taskId) {
        return dependencyService.list(taskId);
    }

    @PostMapping("/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public DependencyResponse addDependency(
            @PathVariable Long taskId,
            @Valid @RequestBody DependencyRequest request) {
        return dependencyService.add(taskId, request);
    }

    @DeleteMapping("/{taskId}/dependencies/{blockerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDependency(@PathVariable Long taskId, @PathVariable Long blockerId) {
        dependencyService.remove(taskId, blockerId);
    }
}
