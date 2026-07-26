package collabdesk.workspacemember.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRoleRequest(
        @NotNull
        WorkspaceRole role
) {
}
