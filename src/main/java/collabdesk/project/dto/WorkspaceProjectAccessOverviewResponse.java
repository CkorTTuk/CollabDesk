package collabdesk.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Projects and project-member role assignments visible in a workspace")
public record WorkspaceProjectAccessOverviewResponse(
        List<ProjectAccessOverviewResponse> projects
) {
}
