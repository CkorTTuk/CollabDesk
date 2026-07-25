package collabdesk.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.project.dto.CreateProjectRequest;
import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
public class ProjectController {
    private final ProjectService projectService;
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
            ){
        return projectService.create(
                workspaceId,
                principal.getUserId(),
                request.name(),
                request.description());

    }
    @GetMapping
    public List<ProjectResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ){
        return projectService.findForWorkspace(
                workspaceId,
                principal.getUserId()
        );
    }
}
