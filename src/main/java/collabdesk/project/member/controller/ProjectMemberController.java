package collabdesk.project.member.controller;

import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.project.role.dto.ReplaceProjectMemberRolesRequest;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.project.member.dto.AddProjectMemberRequest;
import collabdesk.project.member.dto.ProjectMemberResponse;
import collabdesk.project.member.service.ProjectMemberService;
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

/** HTTP boundary for project members and their custom role assignments. */
@RestController
@RequestMapping(
        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/members"
)
@Tag(name = "Project members", description = "Project team management")
@SecurityRequirement(name = "sessionCookie")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    @Operation(
            summary = "List project members",
            description = "Returns workspace members included in the project"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project team"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace or project is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public List<ProjectMemberResponse> findAll(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return projectMemberService.findAll(
                workspaceId,
                projectId,
                principal.getUserId()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a project member",
            description = "Adds an existing workspace member. Requires OWNER or ADMIN."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Member added"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager role required"),
            @ApiResponse(responseCode = "404", description = "Member or project not found"),
            @ApiResponse(responseCode = "409", description = "Member already added")
    })
    public ProjectMemberResponse add(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return projectMemberService.add(
                workspaceId,
                projectId,
                principal.getUserId(),
                request.workspaceMemberId(),
                request.roleIds()
        );
    }

    @PutMapping("/{projectMemberId}/roles")
    @Operation(
            summary = "Replace a project member's custom roles",
            description = "Replaces the complete role assignment. Requires OWNER or ADMIN."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles replaced"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager role required"),
            @ApiResponse(responseCode = "404", description = "Member or role not found")
    })
    public ProjectMemberResponse replaceRoles(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long projectMemberId,
            @Valid @RequestBody ReplaceProjectMemberRolesRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return projectMemberService.replaceRoles(
                workspaceId,
                projectId,
                projectMemberId,
                principal.getUserId(),
                request.roleIds()
        );
    }

    @DeleteMapping("/{projectMemberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a project member",
            description = "Removes the member and their task assignments. Requires OWNER or ADMIN."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager role required"),
            @ApiResponse(responseCode = "404", description = "Member or project not found")
    })
    public void remove(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long projectMemberId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        projectMemberService.remove(
                workspaceId,
                projectId,
                projectMemberId,
                principal.getUserId()
        );
    }
}
