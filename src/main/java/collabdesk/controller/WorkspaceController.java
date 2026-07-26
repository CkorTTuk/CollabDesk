package collabdesk.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.workspace.dto.CreateWorkspaceRequest;
import collabdesk.workspace.dto.WorkspaceResponse;
import collabdesk.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")

@Tag(
        name = "Workspaces",
        description = "Workspace creation and discovery"
)
@SecurityRequirement(name = "sessionCookie")
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a workspace",
            description = "Creates a workspace and makes the current user its OWNER"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workspace created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
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
    @Operation(
            summary = "List current user's workspaces",
            description = "Returns workspaces where the current user has membership"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workspace list"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<WorkspaceResponse> findCurrentUserWorkspaces(@AuthenticationPrincipal AuthenticatedUserPrincipal principal){
        return workspaceService.findForUser(principal.getUserId());
    }
}
