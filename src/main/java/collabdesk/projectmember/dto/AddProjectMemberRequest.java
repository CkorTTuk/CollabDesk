package collabdesk.projectmember.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Workspace member to add to a project")
public record AddProjectMemberRequest(
        @NotNull Long workspaceMemberId
) {
}
