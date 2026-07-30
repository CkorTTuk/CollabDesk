package collabdesk.project.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Complete custom-role assignment for a project member")
public record ReplaceProjectMemberRolesRequest(
        @NotNull
        @Size(max = 100)
        Set<@NotNull Long> roleIds
) {
}
