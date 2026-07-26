package collabdesk.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Data required to create a task")
public record CreateTaskRequest(
        @Schema(example = "Implement workspace member API")
        @NotBlank
        @Size(min = 2, max = 150)
        String title,

        @Schema(
                example = "Add endpoints, authorization and tests",
                nullable = true
        )
        @Size(max = 1000)
        String description
) {
}
