package collabdesk.task.dto;

import collabdesk.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New task workflow status")
public record UpdateTaskStatusRequest(
        @Schema(
                example = "IN_PROGRESS",
                allowableValues = {"TODO", "IN_PROGRESS", "DONE"}
        )
        @NotNull
        TaskStatus status
) {
}
