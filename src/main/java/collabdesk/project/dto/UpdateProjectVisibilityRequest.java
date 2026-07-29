package collabdesk.project.dto;

import collabdesk.project.entity.ProjectVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New project visibility")
public record UpdateProjectVisibilityRequest(
        @NotNull ProjectVisibility visibility
) {
}
