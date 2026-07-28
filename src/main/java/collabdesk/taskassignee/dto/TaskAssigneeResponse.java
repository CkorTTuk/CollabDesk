package collabdesk.taskassignee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A project member assigned to a task")
public record TaskAssigneeResponse(
        Long projectMemberId,
        Long userId,
        String email,
        String displayName
) {
}
