package collabdesk.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Data required to create a workspace")
public record CreateWorkspaceRequest(
        @Schema(example = "Product Team")
        @NotBlank
        @Size(min = 2, max = 100)
        String name,
        @Schema(
                example = "Workspace for the CollabDesk product team",
                nullable = true
        )
        @Size(max = 500)
        String description
) {
}
