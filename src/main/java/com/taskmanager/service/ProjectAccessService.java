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

    public ProjectRole requireRole(Project project, User user, ProjectRole minimumRole) {
        ProjectMember member = requireMembership(project, user);
        if (!hasAtLeastRole(member.getRole(), minimumRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "Access denied");
        }
        return member.getRole();
    }

    public void requireCanRead(Project project, User user) {
        requireMembership(project, user);
    }

    public void requireCanWriteContent(Project project, User user) {
        requireRole(project, user, ProjectRole.EDITOR);
    }

    public void requireCanManageProject(Project project, User user) {
        requireRole(project, user, ProjectRole.OWNER);
    }

    public boolean hasAtLeastRole(ProjectRole actual, ProjectRole required) {
        return actual.ordinal() <= required.ordinal();
    }
}
