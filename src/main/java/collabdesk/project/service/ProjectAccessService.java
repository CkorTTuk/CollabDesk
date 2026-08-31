package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectAccessService {

    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAllowedRoleRepository projectAllowedRoleRepository;
    public ProjectAccessService(
            WorkspaceAccessService workspaceAccessService,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectAllowedRoleRepository projectAllowedRoleRepository
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAllowedRoleRepository = projectAllowedRoleRepository;
    }

    @Transactional(readOnly = true)
    public AccessibleProject requireAccessibleProject(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        WorkspaceMember membership = workspaceAccessService.requireMember(
                workspaceId,
                currentUserId
        );
        Project project = findProject(workspaceId, projectId);
        boolean restricted = projectMemberRepository
                .existsByProject_IdAndGrantsAccessTrue(projectId)
                || projectAllowedRoleRepository.existsByProject_Id(projectId);
        if (isManager(membership.getRole())
                || java.util.Objects.equals(project.getCreatedBy().getId(), currentUserId)
                || !restricted
                || projectMemberRepository
                .existsByProject_IdAndWorkspaceMember_User_IdAndGrantsAccessTrue(
                        projectId,
                        currentUserId
                )
                || projectAllowedRoleRepository.existsAllowedRoleForUser(
                        projectId,
                        membership.getId()
                )) {
            return new AccessibleProject(project, membership);
        }

        throw new ProjectNotFoundException(
                "Project was not found or is not accessible"
        );
    }

    @Transactional(readOnly = true)
    public AccessibleProject requireWritableProject(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        AccessibleProject access = requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        if (access.membership().getRole() == WorkspaceRole.VIEWER) {
            throw new WorkspaceOperationForbiddenException(
                    "Viewer has read-only access"
            );
        }
        return access;
    }

    @Transactional(readOnly = true)
    public List<Project> findAccessibleProjects(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership = workspaceAccessService.requireMember(
                workspaceId,
                currentUserId
        );
        return projectRepository.findAccessibleForWorkspace(
                workspaceId,
                currentUserId,
                membership.getId(),
                isManager(membership.getRole())
        );
    }

    private Project findProject(Long workspaceId, Long projectId) {
        return projectRepository
                .findByIdAndWorkspace_Id(projectId, workspaceId)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project was not found"
                ));
    }

    private boolean isManager(WorkspaceRole role) {
        return role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
    }
}
