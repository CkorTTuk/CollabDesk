package collabdesk.project.dto;

import collabdesk.project.entity.ProjectStatus;
import collabdesk.project.member.dto.ProjectMemberAccessResponse;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.time.Instant;

@Schema(description = "Project summary with visible member access assignments")
public record ProjectAccessOverviewResponse(
        Long projectId,
        String name,
        String description,
        ProjectStatus status,
        Long createdById,
        String createdByDisplayName,
        Instant createdAt,
        boolean restricted,
        List<AccessRoleSummaryResponse> allowedRoles,
        List<ProjectMemberAccessResponse> members
) {
}
