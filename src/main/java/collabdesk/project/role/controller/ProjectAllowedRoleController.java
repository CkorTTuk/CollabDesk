package collabdesk.project.role.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.dto.ReplaceProjectMemberRolesRequest;
import collabdesk.project.role.service.ProjectAllowedRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/allowed-roles")
@SecurityRequirement(name = "sessionCookie")
public class ProjectAllowedRoleController {
    private final ProjectAllowedRoleService service;

    public ProjectAllowedRoleController(ProjectAllowedRoleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List custom roles allowed in the project")
    public List<AccessRoleSummaryResponse> findAll(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return service.findAll(workspaceId, projectId, principal.getUserId());
    }

    @PutMapping
    @Operation(summary = "Replace custom roles allowed in the project")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public List<AccessRoleSummaryResponse> replace(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @Valid @RequestBody ReplaceProjectMemberRolesRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return service.replace(
                workspaceId,
                projectId,
                principal.getUserId(),
                request.roleIds()
        );
    }
}
