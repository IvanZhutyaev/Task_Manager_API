package com.taskmanager.web.api;

import com.taskmanager.config.ApiConstants;
import com.taskmanager.service.InvitationService;
import com.taskmanager.service.LabelService;
import com.taskmanager.service.ProjectActivityService;
import com.taskmanager.service.ProjectService;
import com.taskmanager.service.SprintService;
import com.taskmanager.web.api.dto.ActivityResponse;
import com.taskmanager.web.api.dto.CloseSprintRequest;
import com.taskmanager.web.api.dto.InvitationRequest;
import com.taskmanager.web.api.dto.InvitationResponse;
import com.taskmanager.web.api.dto.LabelRequest;
import com.taskmanager.web.api.dto.LabelResponse;
import com.taskmanager.web.api.dto.MemberRequest;
import com.taskmanager.web.api.dto.MemberResponse;
import com.taskmanager.web.api.dto.PageResponse;
import com.taskmanager.web.api.dto.ProjectRequest;
import com.taskmanager.web.api.dto.ProjectResponse;
import com.taskmanager.web.api.dto.SprintRequest;
import com.taskmanager.web.api.dto.SprintResponse;
import com.taskmanager.web.api.dto.TransferOwnershipRequest;
import com.taskmanager.web.api.dto.UpdateMemberRoleRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = ApiConstants.API_V1 + "/projects", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectController {

    private final ProjectService projectService;
    private final InvitationService invitationService;
    private final LabelService labelService;
    private final SprintService sprintService;
    private final ProjectActivityService projectActivityService;

    public ProjectController(
            ProjectService projectService,
            InvitationService invitationService,
            LabelService labelService,
            SprintService sprintService,
            ProjectActivityService projectActivityService) {
        this.projectService = projectService;
        this.invitationService = invitationService;
        this.labelService = labelService;
        this.sprintService = sprintService;
        this.projectActivityService = projectActivityService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId) {
        return projectService.getProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@Valid @RequestBody ProjectRequest request) {
        return projectService.createProject(request);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
    }

    @PostMapping("/{projectId}/transfer-ownership")
    public ProjectResponse transferOwnership(
            @PathVariable Long projectId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        return projectService.transferOwnership(projectId, request);
    }

    @GetMapping("/{projectId}/members")
    public List<MemberResponse> listMembers(@PathVariable Long projectId) {
        return projectService.listMembers(projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody MemberRequest request) {
        return projectService.addMember(projectId, request);
    }

    @PutMapping("/{projectId}/members/{userId}")
    public MemberResponse updateMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return projectService.updateMemberRole(projectId, userId, request);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(projectId, userId);
    }

    @GetMapping("/{projectId}/invitations")
    public List<InvitationResponse> listInvitations(@PathVariable Long projectId) {
        return invitationService.list(projectId);
    }

    @PostMapping("/{projectId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(
            @PathVariable Long projectId,
            @Valid @RequestBody InvitationRequest request) {
        return invitationService.invite(projectId, request);
    }

    @GetMapping("/{projectId}/labels")
    public List<LabelResponse> listLabels(@PathVariable Long projectId) {
        return labelService.list(projectId);
    }

    @PostMapping("/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse createLabel(
            @PathVariable Long projectId,
            @Valid @RequestBody LabelRequest request) {
        return labelService.create(projectId, request);
    }

    @DeleteMapping("/{projectId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLabel(@PathVariable Long projectId, @PathVariable Long labelId) {
        labelService.delete(projectId, labelId);
    }

    @GetMapping("/{projectId}/sprints")
    public List<SprintResponse> listSprints(@PathVariable Long projectId) {
        return sprintService.list(projectId);
    }

    @PostMapping("/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse createSprint(
            @PathVariable Long projectId,
            @Valid @RequestBody SprintRequest request) {
        return sprintService.create(projectId, request);
    }

    @PostMapping("/{projectId}/sprints/{sprintId}/close")
    public SprintResponse closeSprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId,
            @RequestBody(required = false) CloseSprintRequest request) {
        return sprintService.close(projectId, sprintId, request != null ? request : new CloseSprintRequest(null));
    }

    @GetMapping("/{projectId}/activity")
    public PageResponse<ActivityResponse> activity(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        projectService.getProject(projectId);
        return projectActivityService.list(projectService.getProjectOrThrow(projectId), page, size);
    }
}