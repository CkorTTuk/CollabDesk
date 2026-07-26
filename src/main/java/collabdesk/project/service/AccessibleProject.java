package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.workspacemember.entity.WorkspaceMember;

public record AccessibleProject(
        Project project,
        WorkspaceMember membership
) {
}
