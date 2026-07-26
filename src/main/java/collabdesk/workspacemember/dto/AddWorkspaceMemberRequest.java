package collabdesk.workspacemember.dto;

import collabdesk.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Existing account and role to add to a workspace")
public record AddWorkspaceMemberRequest(
        @Schema(
                description = "Email of an existing active CollabDesk account",
                example = "member@example.com"
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(
                example = "MEMBER",
                allowableValues = {"ADMIN", "MEMBER", "VIEWER"}
        )
        @NotNull
        WorkspaceRole role
) {
    public AddWorkspaceMemberRequest {
        email = email == null ? null : email.trim();
    }
}
