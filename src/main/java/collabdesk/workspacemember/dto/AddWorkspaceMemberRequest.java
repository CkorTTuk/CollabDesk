package collabdesk.workspacemember.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddWorkspaceMemberRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotNull
        WorkspaceRole role
) {
    public AddWorkspaceMemberRequest {
        email = email == null ? null : email.trim();
    }
}
