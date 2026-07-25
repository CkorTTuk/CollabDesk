package collabdesk.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank
        @Size(min = 2, max = 100)
        String name,
        @Size(max = 500)
        String description
) {
}
