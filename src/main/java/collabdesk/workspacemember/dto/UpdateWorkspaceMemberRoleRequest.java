package collabdesk.workspacemember.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New role for a non-owner workspace member")
public record UpdateWorkspaceMemberRoleRequest(
        @Schema(
                example = "VIEWER",
                allowableValues = {"ADMIN", "MEMBER", "VIEWER"}
        )
        @NotNull
        WorkspaceRole role
) {
}
