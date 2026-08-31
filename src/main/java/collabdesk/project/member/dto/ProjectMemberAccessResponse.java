package collabdesk.project.member.dto;

import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Timestamp-free project access summary for a workspace member")
public record ProjectMemberAccessResponse(
        Long projectMemberId,
        Long workspaceMemberId,
        Long userId,
        String displayName,
        String email,
        WorkspaceRole workspaceRole,
        boolean grantsAccess,
        List<AccessRoleSummaryResponse> roles
) {
}
