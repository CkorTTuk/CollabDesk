package collabdesk.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank
        @Size(min = 2, max = 150)
        String title,

        @Size(max = 1000)
        String description
) {
}
