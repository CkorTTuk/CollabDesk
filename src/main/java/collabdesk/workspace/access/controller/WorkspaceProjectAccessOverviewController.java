package collabdesk.workspace.access.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.workspace.access.service.WorkspaceProjectAccessOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/project-access-overview")
@Tag(name = "Workspace project access", description = "Aggregated project access read model")
@SecurityRequirement(name = "sessionCookie")
public class WorkspaceProjectAccessOverviewController {

    private final WorkspaceProjectAccessOverviewService overviewService;

    public WorkspaceProjectAccessOverviewController(
            WorkspaceProjectAccessOverviewService overviewService
    ) {
        this.overviewService = overviewService;
    }

    @GetMapping
    @Operation(
            summary = "Get workspace project access overview",
            description = "Returns accessible projects and timestamp-free member role assignments in one batch response"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project access overview"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Workspace access denied",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    public WorkspaceProjectAccessOverviewResponse find(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return overviewService.findForWorkspace(
                workspaceId,
                principal.getUserId()
        );
    }
}
