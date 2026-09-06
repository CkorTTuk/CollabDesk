package collabdesk.project.controller;

import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.project.dto.CreateProjectRequest;
import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.service.ProjectService;
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

/** HTTP boundary for project creation and access-filtered project listing. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")

@Tag(
        name = "Projects",
        description = "Projects contained in a workspace"
)
@SecurityRequirement(name = "sessionCookie")
public class ProjectController {
    private final ProjectService projectService;
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a project",
            description = "Requires the workspace OWNER or ADMIN role"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "403",
                    description = "OWNER or ADMIN role required",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public ProjectResponse create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
            ){
        return projectService.create(
                workspaceId,
                principal.getUserId(),
                request.name(),
                request.description(),
                request.allowedRoleIds(),
                request.allowedWorkspaceMemberIds());

    }
    @GetMapping
    @Operation(
            summary = "List workspace projects",
            description = "Available to every workspace member, including VIEWER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project list"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public List<ProjectResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ){
        return projectService.findForWorkspace(
                workspaceId,
                principal.getUserId()
        );
    }

}
