package collabdesk.workspace.service;

import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central authorization guard for workspace-level operations. Each method
 * returns the caller's membership or fails before protected data is changed.
 */
@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceAccessService(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    /** Requires any active workspace membership. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireMember(
            Long workspaceId,
            Long currentUserId
    ){
        return workspaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, currentUserId)
                .orElseThrow(() -> new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));
    }
    /** Requires the built-in OWNER role for destructive administration. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireOwner(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership =
                requireMember(workspaceId, currentUserId);

        if (membership.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceOperationForbiddenException(
                    "Owner role is required"
            );
        }

        return membership;
    }
    /** Requires a role that may contribute content to the workspace. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireContributor(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership =
                requireMember(workspaceId, currentUserId);

        if (membership.getRole() == WorkspaceRole.VIEWER) {
            throw new WorkspaceOperationForbiddenException(
                    "Viewer has read-only access"
            );
        }

        return membership;
    }

    /** Requires OWNER or ADMIN authority over members and projects. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireManager(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership = requireMember(workspaceId, currentUserId);
        if (membership.getRole() != WorkspaceRole.OWNER
                && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new WorkspaceOperationForbiddenException(
                    "Owner or admin role is required"
            );
        }
        return membership;
    }
}
