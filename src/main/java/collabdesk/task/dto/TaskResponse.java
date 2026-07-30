package collabdesk.task.dto;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.entity.TaskVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import collabdesk.task.assignee.dto.TaskAssigneeResponse;

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
        TaskAssigneeResponse assignee,
        Set<ProjectPermission> currentUserPermissions
) {
}
