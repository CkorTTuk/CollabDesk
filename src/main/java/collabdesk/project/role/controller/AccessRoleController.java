package collabdesk.project.role.controller;

import collabdesk.project.role.dto.*;
import collabdesk.project.role.service.AccessRoleService;
import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.openapi.ApiProblemResponse;
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

/** HTTP boundary for workspace-defined access-role administration. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/access-roles")
@Tag(name = "Access roles", description = "Custom workspace role management")
@SecurityRequirement(name = "sessionCookie")
public class AccessRoleController {

    private final AccessRoleService accessRoleService;

    public AccessRoleController(AccessRoleService accessRoleService) {
        this.accessRoleService = accessRoleService;
    }

    @GetMapping
    @Operation(summary = "List custom workspace roles")
    public List<AccessRoleResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return accessRoleService.findAll(workspaceId, principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a custom workspace role")
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Manager role required"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Role name already exists",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public AccessRoleResponse create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateAccessRoleRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return accessRoleService.create(
                workspaceId,
                principal.getUserId(),
                request
        );
    }

    @PatchMapping("/{roleId}")
    @Operation(summary = "Update a custom workspace role")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public AccessRoleResponse update(
            @PathVariable Long workspaceId,
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateAccessRoleRequest request,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return accessRoleService.update(
                workspaceId,
                roleId,
                principal.getUserId(),
                request
        );
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused custom workspace role")
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Role is assigned to a project member",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public void delete(
            @PathVariable Long workspaceId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        accessRoleService.delete(
                workspaceId,
                roleId,
                principal.getUserId()
        );
    }
}
