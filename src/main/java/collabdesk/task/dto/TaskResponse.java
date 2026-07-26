package collabdesk.task.dto;

import collabdesk.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Task inside a project")
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
