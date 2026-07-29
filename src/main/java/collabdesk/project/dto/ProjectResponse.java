package collabdesk.project.dto;

import collabdesk.project.entity.ProjectStatus;
import collabdesk.project.entity.ProjectVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Project inside a workspace")
public record ProjectResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        ProjectStatus status,
        ProjectVisibility visibility,
        Instant createdAt
) {
}
