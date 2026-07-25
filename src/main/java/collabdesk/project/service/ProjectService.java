package collabdesk.project.service;

import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public ProjectService(
            ProjectRepository projectRepository,
            WorkspaceAccessService workspaceAccessService) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
    }
    @Transactional
    public ProjectResponse create(
            Long workspaceId,
            Long currentUserId,
            String name,
            String description
    ) {
        WorkspaceMember workspaceMember = workspaceAccessService.requireMember(workspaceId, currentUserId);
        Project project =
                new Project(
                workspaceMember.getWorkspace(),
                        name,
                        description,
                        workspaceMember.getUser()
            );
        Project savedProject = projectRepository.save(project);
        return new ProjectResponse(
                savedProject.getId(),
                workspaceId,
                savedProject.getName(),
                savedProject.getDescription(),
                savedProject.getStatus(),
                savedProject.getCreatedAt()
        );
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);
        List<Project> list = projectRepository.findByWorkspace_IdOrderByCreatedAtAsc(workspaceId);
        List<ProjectResponse> projectResponses = new ArrayList<>();
        for (Project project : list) {
            projectResponses.add(new ProjectResponse(
                    project.getId(),
                    workspaceId,
                    project.getName(),
                    project.getDescription(),
                    project.getStatus(),
                    project.getCreatedAt()
            ));
        }
        return List.copyOf(projectResponses);

    }
}
