package collabdesk.task.assignee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The single project member assigned to a task")
public record UpdateTaskAssigneeRequest(
        @Schema(
                description = "Project member id, or null to leave unassigned",
                nullable = true
        )
        Long projectMemberId
) {
}
