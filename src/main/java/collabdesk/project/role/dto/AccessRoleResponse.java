package collabdesk.project.role.dto;

import collabdesk.project.role.entity.ProjectPermission;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "A custom role available throughout a workspace")
public record AccessRoleResponse(
        Long id,
        Long workspaceId,
        String name,
        String color,
        Set<ProjectPermission> permissions,
        Long version
) {
}
