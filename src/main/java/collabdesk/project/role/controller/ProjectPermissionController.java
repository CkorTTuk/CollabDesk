package collabdesk.project.role.controller;

import collabdesk.project.role.dto.EffectivePermissionsResponse;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/permissions"
)
@Tag(name = "Project permissions", description = "Effective project access")
@SecurityRequirement(name = "sessionCookie")
public class ProjectPermissionController {

    private final ProjectAccessService projectAccessService;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectPermissionController(
            ProjectAccessService projectAccessService,
            ProjectPermissionService projectPermissionService,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.projectAccessService = projectAccessService;
        this.projectPermissionService = projectPermissionService;
        this.projectMemberRepository = projectMemberRepository;
    }

    @GetMapping
    @Operation(summary = "Get current user's effective project permissions")
    public EffectivePermissionsResponse findCurrent(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        AccessibleProject access = projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                principal.getUserId()
        );
        boolean usingDefault = access.membership().getRole()
                == WorkspaceRole.MEMBER
                && projectMemberRepository
                .findByProject_IdAndWorkspaceMember_User_Id(
                        projectId,
                        principal.getUserId()
                )
                .map(member -> projectPermissionService
                        .usesDefaultPermissions(member.getId()))
                .orElse(false);
        return new EffectivePermissionsResponse(
                projectPermissionService.findEffectiveProjectPermissions(
                        access,
                        principal.getUserId()
                ),
                usingDefault
        );
    }
}
