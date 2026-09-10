package collabdesk.project.member.dto;

import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Schema(description = "A project member and their workspace identity")
public record ProjectMemberResponse(
        Long id,
        Long workspaceMemberId,
        Long userId,
        String email,
        String displayName,
        String avatarUrl,
        WorkspaceRole workspaceRole,
        Instant joinedAt,
        boolean grantsAccess,
        List<AccessRoleSummaryResponse> roles,
        Set<ProjectPermission> effectivePermissions
) {
}
