package collabdesk.projectmember.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A project member and their workspace identity")
public record ProjectMemberResponse(
        Long id,
        Long workspaceMemberId,
        Long userId,
        String email,
        String displayName,
        WorkspaceRole workspaceRole,
        Instant joinedAt
) {
}
