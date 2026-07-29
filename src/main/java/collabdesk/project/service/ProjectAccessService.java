package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectVisibility;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.projectmember.repository.ProjectMemberRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    public ProjectAccessService(
            WorkspaceAccessService workspaceAccessService,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
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
        if (project.getVisibility() == ProjectVisibility.WORKSPACE
                || isManager(membership.getRole())
                || projectMemberRepository
                .existsByProject_IdAndWorkspaceMember_User_Id(
                        projectId,
                        currentUserId
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
