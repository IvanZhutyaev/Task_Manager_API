package com.taskmanager.service;

import com.taskmanager.domain.Project;
import com.taskmanager.domain.ProjectMember;
import com.taskmanager.domain.ProjectRole;
import com.taskmanager.domain.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.web.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessService(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    public ProjectMember requireMembership(Project project, User user) {
        return projectMemberRepository.findByProjectAndUser(project, user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN.value(), "Access denied"));
    }

    public void requireCanManageProject(Project project, User user) {
        ProjectMember member = requireMembership(project, user);
        if (!member.canManageProject()) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "Only project owner can manage this project");
        }
    }

    public void requireCanWriteContent(Project project, User user) {
        ProjectMember member = requireMembership(project, user);
        if (!member.canWriteContent()) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "Insufficient permissions");
        }
    }

    public void requireCanRead(Project project, User user) {
        ProjectMember member = requireMembership(project, user);
        if (!member.canRead()) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "Access denied");
        }
    }

    public ProjectRole requireRole(Project project, User user, ProjectRole minimumRole) {
        ProjectMember member = requireMembership(project, user);
        if (!hasAtLeastRole(member.getRole(), minimumRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "Access denied");
        }
        return member.getRole();
    }

    public boolean hasAtLeastRole(ProjectRole actual, ProjectRole required) {
        return actual.ordinal() <= required.ordinal();
    }
}
