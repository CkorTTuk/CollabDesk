package collabdesk.task.dto;

import collabdesk.task.entity.TaskStatus;
import collabdesk.task.entity.TaskVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import collabdesk.taskassignee.dto.TaskAssigneeResponse;

@Schema(description = "Task inside a project")
public record TaskResponse(
        Long id,
        Long projectId,
        String title,
        String description,
        TaskStatus status,
        TaskVisibility visibility,
        Long createdById,
        Instant createdAt,
        Instant updatedAt,
        List<TaskAssigneeResponse> assignees
) {
}
