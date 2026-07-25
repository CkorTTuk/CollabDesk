package collabdesk.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank
        @Size(min = 2, max = 100)
        String name,

        @Size(max = 500)
        String description
) {
}
