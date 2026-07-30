package collabdesk.project.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Workspace member to add to a project")
public record AddProjectMemberRequest(
        @NotNull Long workspaceMemberId,
        @NotNull
        @Size(max = 100)
        Set<@NotNull Long> roleIds
) {
}
