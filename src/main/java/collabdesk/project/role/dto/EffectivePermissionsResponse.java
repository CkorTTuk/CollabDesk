package collabdesk.project.role.dto;

import collabdesk.project.role.entity.ProjectPermission;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Permissions effective for the current project")
public record EffectivePermissionsResponse(
        Set<ProjectPermission> permissions,
        boolean usingDefaultPermissions
) {
}
