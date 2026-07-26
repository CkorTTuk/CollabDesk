package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectRepository projectRepository;

    public ProjectAccessService(
            WorkspaceAccessService workspaceAccessService,
            ProjectRepository projectRepository
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectRepository = projectRepository;
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

        return new AccessibleProject(
                findProject(workspaceId, projectId),
                membership
        );
    }

    @Transactional(readOnly = true)
    public AccessibleProject requireWritableProject(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        WorkspaceMember membership = workspaceAccessService.requireContributor(
                workspaceId,
                currentUserId
        );

        return new AccessibleProject(
                findProject(workspaceId, projectId),
                membership
        );
    }

    private Project findProject(Long workspaceId, Long projectId) {
        return projectRepository
                .findByIdAndWorkspace_Id(projectId, workspaceId)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project was not found"
                ));
    }
}
