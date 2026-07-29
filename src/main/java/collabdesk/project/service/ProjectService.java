package collabdesk.project.service;

import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectVisibility;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.projectmember.repository.ProjectMemberRepository;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            WorkspaceAccessService workspaceAccessService,
            ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.projectMemberRepository = projectMemberRepository;
    }
    @Transactional
    public ProjectResponse create(
            Long workspaceId,
            Long currentUserId,
            String name,
            String description
    ) {
        WorkspaceMember workspaceMember = workspaceAccessService.requireContributor(workspaceId, currentUserId);
        Project project =
                new Project(
                workspaceMember.getWorkspace(),
                        name,
                        description,
                        workspaceMember.getUser()
            );
        Project savedProject = projectRepository.save(project);
        projectMemberRepository.save(
                new ProjectMember(savedProject, workspaceMember)
        );
        return toResponse(savedProject);
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership =
                workspaceAccessService.requireMember(workspaceId, currentUserId);
        boolean manager = membership.getRole() == WorkspaceRole.OWNER
                || membership.getRole() == WorkspaceRole.ADMIN;
        return projectRepository.findAccessibleForWorkspace(
                        workspaceId,
                        currentUserId,
                        manager
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse changeVisibility(
            Long workspaceId,
            Long projectId,
            Long currentUserId,
            ProjectVisibility visibility
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        Project project = projectRepository
                .findByIdAndWorkspace_Id(projectId, workspaceId)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project was not found"
                ));
        project.changeVisibility(visibility);
        return toResponse(project);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspace().getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getVisibility(),
                project.getCreatedAt()
        );
    }
}
