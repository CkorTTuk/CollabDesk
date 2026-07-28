package collabdesk.taskassignee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Complete replacement for a task's assignee list")
public record ReplaceTaskAssigneesRequest(
        @NotNull
        @Size(max = 20)
        Set<@NotNull Long> projectMemberIds
) {
}
