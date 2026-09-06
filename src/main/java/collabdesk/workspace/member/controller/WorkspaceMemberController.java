package collabdesk.workspace.member.controller;

import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.workspace.member.dto.AddWorkspaceMemberRequest;
import collabdesk.workspace.member.dto.UpdateWorkspaceMemberRoleRequest;
import collabdesk.workspace.member.dto.WorkspaceMemberResponse;
import collabdesk.workspace.member.service.WorkspaceMemberService;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.dto.ReplaceProjectMemberRolesRequest;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP boundary for workspace membership, roles and member removal. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")

@Tag(
        name = "Workspace members",
        description = "Workspace membership and role management"
)
@SecurityRequirement(name = "sessionCookie")
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;
    private final WorkspaceMemberAccessRoleService memberAccessRoleService;

    public WorkspaceMemberController(
            WorkspaceMemberService workspaceMemberService,
            WorkspaceMemberAccessRoleService memberAccessRoleService
    ) {
        this.workspaceMemberService = workspaceMemberService;
        this.memberAccessRoleService = memberAccessRoleService;
    }

    @GetMapping
    @Operation(
            summary = "List workspace members",
            description = "Available to every member of the workspace"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workspace member list"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public List<WorkspaceMemberResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return workspaceMemberService.findForWorkspace(
                workspaceId,
                principal.getUserId()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a workspace member",
            description = """
                    Adds an existing active CollabDesk account by email.
                    Requires the OWNER or ADMIN workspace role.
                    """
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workspace member added"),
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
                    description = "Workspace or account not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Membership already exists or OWNER role requested",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public WorkspaceMemberResponse add(
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return workspaceMemberService.add(
                workspaceId,
                principal.getUserId(),
                request.email(),
                request.role()
        );
    }

    @PatchMapping("/{memberId}/role")
    @Operation(
            summary = "Change a workspace member role",
            description = "Changes a non-owner member role. Requires OWNER or ADMIN."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member role changed"),
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
                    description = "Workspace member not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "OWNER membership cannot be changed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public WorkspaceMemberResponse changeRole(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return workspaceMemberService.changeRole(
                workspaceId,
                memberId,
                principal.getUserId(),
                request.role()
        );
    }

    @PutMapping("/{memberId}/access-roles")
    @Operation(summary = "Replace a workspace member's custom access roles")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public List<AccessRoleSummaryResponse> replaceAccessRoles(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody ReplaceProjectMemberRolesRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return memberAccessRoleService.replace(
                workspaceId,
                memberId,
                principal.getUserId(),
                request.roleIds()
        );
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a workspace member",
            description = "Removes a non-owner membership. The User account is preserved."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Workspace member removed"),
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
                    description = "Workspace member not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "OWNER membership cannot be removed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public void remove(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        workspaceMemberService.remove(
                workspaceId,
                memberId,
                principal.getUserId()
        );
    }
}
