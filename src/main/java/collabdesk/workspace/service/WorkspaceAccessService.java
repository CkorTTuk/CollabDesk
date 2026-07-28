package collabdesk.workspace.service;

import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspacemember.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceAccessService(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

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
