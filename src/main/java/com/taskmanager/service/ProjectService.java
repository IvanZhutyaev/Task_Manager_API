package com.taskmanager.service;

import com.taskmanager.domain.Project;
import com.taskmanager.domain.ProjectMember;
import com.taskmanager.domain.ProjectRole;
import com.taskmanager.domain.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.MemberRequest;
import com.taskmanager.web.api.dto.MemberResponse;
import com.taskmanager.web.api.dto.ProjectRequest;
import com.taskmanager.web.api.dto.ProjectResponse;
import com.taskmanager.web.api.dto.UpdateMemberRoleRequest;
import com.taskmanager.web.exception.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectAccessService projectAccessService,
            CurrentUserService currentUserService,
            UserService userService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects() {
        User user = currentUserService.getCurrentUser();
        return projectRepository.findAllByMember(user).stream()
                .map(project -> {
                    ProjectRole role = projectAccessService.requireMembership(project, user).getRole();
                    return ProjectResponse.from(project, role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User user = currentUserService.getCurrentUser();
        ProjectRole role = projectAccessService.requireMembership(project, user).getRole();
        return ProjectResponse.from(project, role);
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        User owner = currentUserService.getCurrentUser();

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwner(owner);
        project = projectRepository.save(project);

        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProject(project);
        ownerMember.setUser(owner);
        ownerMember.setRole(ProjectRole.OWNER);
        projectMemberRepository.save(ownerMember);

        log.info("Project created: id={}, owner={}", project.getId(), owner.getEmail());
        return ProjectResponse.from(project, ProjectRole.OWNER);
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanManageProject(project, currentUserService.getCurrentUser());

        project.setName(request.name());
        project.setDescription(request.description());
        project = projectRepository.save(project);

        log.info("Project updated: id={}", projectId);
        return ProjectResponse.from(project, ProjectRole.OWNER);
    }

    @Transactional
    public void deleteProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanManageProject(project, currentUserService.getCurrentUser());
        projectRepository.delete(project);
        log.info("Project deleted: id={}", projectId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanRead(project, currentUserService.getCurrentUser());
        return projectMemberRepository.findByProject(project).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public MemberResponse addMember(Long projectId, MemberRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanManageProject(project, currentUserService.getCurrentUser());

        if (request.role() == ProjectRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot assign OWNER role to a member");
        }

        User user = userService.getUserById(request.userId());
        if (projectMemberRepository.existsByProjectAndUser(project, user)) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "User is already a project member");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(request.role());
        projectMemberRepository.save(member);

        log.info("Member added to project {}: userId={}, role={}", projectId, user.getId(), request.role());
        return toMemberResponse(member);
    }

    @Transactional
    public MemberResponse updateMemberRole(Long projectId, Long userId, UpdateMemberRoleRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanManageProject(project, currentUserService.getCurrentUser());

        if (request.role() == ProjectRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot assign OWNER role to a member");
        }

        User user = userService.getUserById(userId);
        ProjectMember member = projectMemberRepository.findByProjectAndUser(project, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Member not found"));

        if (member.getRole() == ProjectRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot change owner's role");
        }

        member.setRole(request.role());
        return toMemberResponse(projectMemberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.requireCanManageProject(project, currentUserService.getCurrentUser());

        User user = userService.getUserById(userId);
        ProjectMember member = projectMemberRepository.findByProjectAndUser(project, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Member not found"));

        if (member.getRole() == ProjectRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "Cannot remove project owner");
        }

        projectMemberRepository.delete(member);
        log.info("Member removed from project {}: userId={}", projectId, userId);
    }

    public Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Project not found"));
    }

    private MemberResponse toMemberResponse(ProjectMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getName(),
                member.getRole()
        );
    }
}
