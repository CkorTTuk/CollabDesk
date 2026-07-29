package collabdesk.task.dto;

import collabdesk.task.entity.TaskVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New task visibility")
public record UpdateTaskVisibilityRequest(
        @NotNull TaskVisibility visibility
) {
}
