package collabdesk.workspace.dto;

import collabdesk.workspace.entity.WorkspaceRole;

import java.time.Instant;

public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        WorkspaceRole role,
        Instant createdAt
) {
}
