package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.workspace.member.entity.WorkspaceMember;

public record AccessibleProject(
        Project project,
        WorkspaceMember membership
) {
}
