package collabdesk.project.role.service;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.task.service.AccessibleTask;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ProjectPermissionService {

    public static final Set<ProjectPermission> BASE_MEMBER_PERMISSIONS =
            Set.of();

    private final ProjectAllowedRoleRepository projectAllowedRoleRepository;

    public ProjectPermissionService(
            ProjectAllowedRoleRepository projectAllowedRoleRepository
    ) {
        this.projectAllowedRoleRepository = projectAllowedRoleRepository;
    }

    @Transactional(readOnly = true)
    public void requireProjectPermission(
            AccessibleProject access,
            Long currentUserId,
            ProjectPermission permission
    ) {
        require(
                findEffectiveProjectPermissions(access, currentUserId),
                permission
        );
    }

    @Transactional(readOnly = true)
    public void requireTaskPermission(
            AccessibleTask access,
            Long currentUserId,
            ProjectPermission permission
    ) {
        requireProjectPermission(
                access.projectAccess(),
                currentUserId,
                permission
        );
    }

    @Transactional(readOnly = true)
    public Set<ProjectPermission> findEffectiveProjectPermissions(
            AccessibleProject access,
            Long currentUserId
    ) {
        WorkspaceRole workspaceRole = access.membership().getRole();
        if (workspaceRole == WorkspaceRole.OWNER
                || workspaceRole == WorkspaceRole.ADMIN) {
            return Set.of(ProjectPermission.EDIT_PROJECT);
        }
        if (workspaceRole == WorkspaceRole.VIEWER) {
            return Set.of();
        }

        return projectAllowedRoleRepository.canEditProject(
                access.project().getId(),
                access.membership().getId()
        ) ? Set.of(ProjectPermission.EDIT_PROJECT) : Set.of();
    }

    @Transactional(readOnly = true)
    public Set<ProjectPermission> findEffectiveTaskPermissions(
            AccessibleTask access,
            Long currentUserId
    ) {
        return findEffectiveProjectPermissions(
                access.projectAccess(),
                currentUserId
        );
    }

    @Transactional(readOnly = true)
    public Set<ProjectPermission> findForMember(
            Long projectId,
            WorkspaceMember member
    ) {
        if (member.getRole() == WorkspaceRole.OWNER
                || member.getRole() == WorkspaceRole.ADMIN) {
            return Set.of(ProjectPermission.EDIT_PROJECT);
        }
        if (member.getRole() == WorkspaceRole.VIEWER) {
            return Set.of();
        }
        return projectAllowedRoleRepository.canEditProject(projectId, member.getId())
                ? Set.of(ProjectPermission.EDIT_PROJECT)
                : Set.of();
    }

    @Transactional(readOnly = true)
    public boolean usesDefaultPermissions(Long projectMemberId) {
        return false;
    }

    private void require(
            Set<ProjectPermission> effective,
            ProjectPermission required
    ) {
        if (!effective.contains(required)) {
            throw new WorkspaceOperationForbiddenException(
                    "Project permission is required: " + required
            );
        }
    }
}
