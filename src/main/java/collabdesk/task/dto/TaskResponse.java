package collabdesk.task.dto;

import collabdesk.task.entity.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        Long id,
        Long projectId,
        String title,
        String description,
        TaskStatus status,
        Long createdById,
        Instant createdAt,
        Instant updatedAt
) {
}
