package collabdesk.workspace.access.service;

import collabdesk.project.dto.ProjectAccessOverviewResponse;
import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.member.dto.ProjectMemberAccessResponse;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.service.ProjectMemberRoleService;
import collabdesk.project.role.service.ProjectMemberRoleSnapshot;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkspaceProjectAccessOverviewService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberRoleService projectMemberRoleService;

    public WorkspaceProjectAccessOverviewService(
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            ProjectMemberRoleService projectMemberRoleService
    ) {
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMemberRoleService = projectMemberRoleService;
    }

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
        ProjectMemberRoleSnapshot roleSnapshot = projectMemberRoleService
                .loadFor(members.stream().map(ProjectMember::getId).toList());
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
                                roleSnapshot
                        ))
                        .toList()
        );
    }

    private ProjectAccessOverviewResponse toProjectResponse(
            Project project,
            List<ProjectMember> members,
            ProjectMemberRoleSnapshot roleSnapshot
    ) {
        return new ProjectAccessOverviewResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getVisibility(),
                members.stream()
                        .map(member -> toMemberResponse(member, roleSnapshot))
                        .toList()
        );
    }

    private ProjectMemberAccessResponse toMemberResponse(
            ProjectMember member,
            ProjectMemberRoleSnapshot roleSnapshot
    ) {
        WorkspaceMember workspaceMember = member.getWorkspaceMember();
        return new ProjectMemberAccessResponse(
                member.getId(),
                workspaceMember.getId(),
                workspaceMember.getUser().getId(),
                workspaceMember.getUser().getDisplayName(),
                workspaceMember.getUser().getEmail(),
                workspaceMember.getRole(),
                roleSnapshot.roles().getOrDefault(member.getId(), List.of())
        );
    }
}
