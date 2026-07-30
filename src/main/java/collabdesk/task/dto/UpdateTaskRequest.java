package collabdesk.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable task content")
public record UpdateTaskRequest(
        @NotBlank
        @Size(min = 2, max = 150)
        String title,

        @Size(max = 1000)
        String description
) {
}
