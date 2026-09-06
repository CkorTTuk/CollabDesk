package collabdesk.workspace.access.service;

import collabdesk.project.dto.ProjectAccessOverviewResponse;
import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.member.dto.ProjectMemberAccessResponse;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.project.role.entity.ProjectAllowedRole;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the complete project-access projection from database state. This is
 * the authoritative query used when no cached overview is available.
 */
@Service
public class WorkspaceProjectAccessOverviewQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberAccessRoleService memberAccessRoleService;
    private final ProjectAllowedRoleRepository allowedRoleRepository;

    public WorkspaceProjectAccessOverviewQueryService(
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberAccessRoleService memberAccessRoleService,
            ProjectAllowedRoleRepository allowedRoleRepository
    ) {
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.memberAccessRoleService = memberAccessRoleService;
        this.allowedRoleRepository = allowedRoleRepository;
    }

    /** Calculates the overview directly from current database associations. */
    @Transactional(readOnly = true)
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        List<Project> projects = projectAccessService.findAccessibleProjects(
                workspaceId,
                currentUserId
        );
        if (projects.isEmpty()) {
            return new WorkspaceProjectAccessOverviewResponse(List.of());
        }

        List<ProjectMember> members = projectMemberRepository
                .findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
                        projects.stream().map(Project::getId).toList()
                );
        Map<Long, List<AccessRoleSummaryResponse>> memberRoles = memberAccessRoleService
                .findForMembers(members.stream()
                        .map(item -> item.getWorkspaceMember().getId())
                        .toList());
        Map<Long, List<ProjectAllowedRole>> allowedRoles = allowedRoleRepository
                .findByProject_IdIn(projects.stream().map(Project::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(item -> item.getProject().getId()));
        Map<Long, List<ProjectMember>> membersByProject = members.stream()
                .collect(Collectors.groupingBy(
                        member -> member.getProject().getId()
                ));

        return new WorkspaceProjectAccessOverviewResponse(
                projects.stream()
                        .map(project -> toProjectResponse(
                                project,
                                membersByProject.getOrDefault(
                                        project.getId(),
                                        List.of()
                                ),
                                memberRoles,
                                allowedRoles.getOrDefault(project.getId(), List.of())
                        ))
                        .toList()
        );
    }

    private ProjectAccessOverviewResponse toProjectResponse(
            Project project,
            List<ProjectMember> members,
            Map<Long, List<AccessRoleSummaryResponse>> memberRoles,
            List<ProjectAllowedRole> allowedRoles
    ) {
        return new ProjectAccessOverviewResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy().getId(),
                project.getCreatedBy().getDisplayName(),
                project.getCreatedAt(),
                members.stream().anyMatch(ProjectMember::isGrantsAccess)
                        || !allowedRoles.isEmpty(),
                allowedRoles.stream()
                        .map(item -> new AccessRoleSummaryResponse(
                                item.getRole().getId(),
                                item.getRole().getName(),
                                item.getRole().getColor()
                        ))
                        .toList(),
                members.stream()
                        .map(member -> toMemberResponse(member, memberRoles))
                        .toList()
        );
    }

    private ProjectMemberAccessResponse toMemberResponse(
            ProjectMember member,
            Map<Long, List<AccessRoleSummaryResponse>> memberRoles
    ) {
        WorkspaceMember workspaceMember = member.getWorkspaceMember();
        return new ProjectMemberAccessResponse(
                member.getId(),
                workspaceMember.getId(),
                workspaceMember.getUser().getId(),
                workspaceMember.getUser().getDisplayName(),
                workspaceMember.getUser().getEmail(),
                workspaceMember.getRole(),
                member.isGrantsAccess(),
                memberRoles.getOrDefault(workspaceMember.getId(), List.of())
        );
    }
}
