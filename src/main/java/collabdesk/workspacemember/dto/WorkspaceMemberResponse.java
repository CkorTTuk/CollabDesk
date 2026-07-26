package collabdesk.workspacemember.dto;

import collabdesk.workspace.entity.WorkspaceRole;

import java.time.Instant;

public record WorkspaceMemberResponse(
        Long id,
        Long userId,
        String email,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {
}