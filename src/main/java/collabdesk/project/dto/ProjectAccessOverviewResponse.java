package collabdesk.project.dto;

import collabdesk.project.entity.ProjectStatus;
import collabdesk.project.entity.ProjectVisibility;
import collabdesk.project.member.dto.ProjectMemberAccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Project summary with visible member access assignments")
public record ProjectAccessOverviewResponse(
        Long projectId,
        String name,
        String description,
        ProjectStatus status,
        ProjectVisibility visibility,
        List<ProjectMemberAccessResponse> members
) {
}
