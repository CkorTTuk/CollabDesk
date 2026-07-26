package collabdesk.workspace.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Workspace visible to the current user")
public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        WorkspaceRole role,
        Instant createdAt
) {
}
