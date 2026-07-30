package collabdesk.project.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A custom role assigned in a project")
public record AccessRoleSummaryResponse(
        Long id,
        String name,
        String color
) {
}
