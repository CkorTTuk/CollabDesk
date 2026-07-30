package collabdesk.project.role.service;

import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.ProjectPermission;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ProjectMemberRoleSnapshot(
        Map<Long, List<AccessRoleSummaryResponse>> roles,
        Map<Long, Set<ProjectPermission>> permissions,
        Set<Long> membersWithCustomRoles
) {
    public static ProjectMemberRoleSnapshot empty() {
        return new ProjectMemberRoleSnapshot(Map.of(), Map.of(), Set.of());
    }
}
