package collabdesk.workspace.member.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Workspace membership with account information")
public record WorkspaceMemberResponse(
        Long id,
        Long userId,
        String email,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {
}
