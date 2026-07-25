package collabdesk.project.dto;

import collabdesk.project.entity.ProjectStatus;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt
) {
}
