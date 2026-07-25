package collabdesk.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.workspace.dto.CreateWorkspaceRequest;
import collabdesk.workspace.dto.WorkspaceResponse;
import collabdesk.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")

public class WorkspaceController {
    private final WorkspaceService workspaceService;
    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @Valid @RequestBody CreateWorkspaceRequest createWorkspaceRequest,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
            ){
        return workspaceService.create(
                principal.getUserId(),
                createWorkspaceRequest.name(),
                createWorkspaceRequest.description()
        );
    }
    @GetMapping
    public List<WorkspaceResponse> findCurrentUserWorkspaces(@AuthenticationPrincipal AuthenticatedUserPrincipal principal){
        return workspaceService.findForUser(principal.getUserId());
    }
}
