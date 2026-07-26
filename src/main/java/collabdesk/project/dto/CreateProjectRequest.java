package collabdesk.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Data required to create a project")
public record CreateProjectRequest(
        @Schema(example = "CollabDesk MVP")
        @NotBlank
        @Size(min = 2, max = 100)
        String name,

        @Schema(example = "First usable CollabDesk release", nullable = true)
        @Size(max = 500)
        String description
) {
}
