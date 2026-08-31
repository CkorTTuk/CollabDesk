package collabdesk.task.activity.dto;

import collabdesk.task.activity.entity.TaskActivityType;

import java.time.Instant;

public record TaskActivityResponse(
        Long id,
        TaskActivityType type,
        Long actorId,
        String actorDisplayName,
        String oldValue,
        String newValue,
        Instant createdAt
) {
}
