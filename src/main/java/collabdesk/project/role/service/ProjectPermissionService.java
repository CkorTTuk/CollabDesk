package collabdesk.project.role.service;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.task.service.AccessibleTask;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class ProjectPermissionService {

    public static final Set<ProjectPermission> BASE_MEMBER_PERMISSIONS =
            Set.copyOf(EnumSet.allOf(ProjectPermission.class));

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberRoleService projectMemberRoleService;

    public ProjectPermissionService(
            ProjectMemberRepository projectMemberRepository,
            ProjectMemberRoleService projectMemberRoleService
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectMemberRoleService = projectMemberRoleService;
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
            return Set.copyOf(EnumSet.allOf(ProjectPermission.class));
        }
        if (workspaceRole == WorkspaceRole.VIEWER) {
            return Set.of();
        }

        ProjectMember projectMember = projectMemberRepository
                .findByProject_IdAndWorkspaceMember_User_Id(
                        access.project().getId(),
                        currentUserId
                )
                .orElse(null);
        if (projectMember == null) {
            return Set.of();
        }

        ProjectMemberRoleSnapshot snapshot =
                projectMemberRoleService.loadFor(Set.of(projectMember.getId()));
        if (!snapshot.membersWithCustomRoles().contains(projectMember.getId())) {
            return BASE_MEMBER_PERMISSIONS;
        }
        return snapshot.permissions().getOrDefault(
                projectMember.getId(),
                Set.of()
        );
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
    public boolean usesDefaultPermissions(Long projectMemberId) {
        return !projectMemberRoleService.loadFor(Set.of(projectMemberId))
                .membersWithCustomRoles()
                .contains(projectMemberId);
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
