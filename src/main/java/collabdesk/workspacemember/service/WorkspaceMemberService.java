package collabdesk.workspacemember.service;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceMemberAlreadyExistsException;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import collabdesk.workspace.service.exceptions.WorkspaceUserNotFoundException;
import collabdesk.workspacemember.dto.WorkspaceMemberResponse;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspacemember.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class WorkspaceMemberService {
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserRepository userRepository;
    public WorkspaceMemberService(
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAccessService workspaceAccessService,
            UserRepository userRepository
    ) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userRepository = userRepository;
    }
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);

        return workspaceMemberRepository
                .findByWorkspace_IdOrderByJoinedAtAsc(workspaceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse add(
            Long workspaceId,
            Long currentUserId,
            String email,
            WorkspaceRole role
    ) {
        WorkspaceMember ownerMembership =
                workspaceAccessService.requireOwner(workspaceId, currentUserId);

        rejectOwnerRole(role);

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new WorkspaceUserNotFoundException(
                        "Active user was not found"
                ));

        if (workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                workspaceId,
                user.getId()
        )) {
            throw new WorkspaceMemberAlreadyExistsException(
                    "User is already a workspace member"
            );
        }

        WorkspaceMember membership = WorkspaceMember.collaborator(
                ownerMembership.getWorkspace(),
                user,
                role
        );

        return toResponse(workspaceMemberRepository.save(membership));
    }

    @Transactional
    public WorkspaceMemberResponse changeRole(
            Long workspaceId,
            Long memberId,
            Long currentUserId,
            WorkspaceRole newRole
    ) {
        workspaceAccessService.requireOwner(workspaceId, currentUserId);
        rejectOwnerRole(newRole);

        WorkspaceMember membership = findScopedMember(workspaceId, memberId);
        if (membership.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner role cannot be changed"
            );
        }

        membership.changeRole(newRole);
        return toResponse(membership);
    }

    @Transactional
    public void remove(
            Long workspaceId,
            Long memberId,
            Long currentUserId
    ) {
        workspaceAccessService.requireOwner(workspaceId, currentUserId);

        WorkspaceMember membership = findScopedMember(workspaceId, memberId);
        if (membership.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner membership cannot be removed"
            );
        }

        workspaceMemberRepository.delete(membership);
    }

    private WorkspaceMember findScopedMember(Long workspaceId, Long memberId) {
        return workspaceMemberRepository
                .findByIdAndWorkspace_Id(memberId, workspaceId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "Workspace member was not found"
                ));
    }

    private void rejectOwnerRole(WorkspaceRole role) {
        if (role == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner role cannot be assigned through member management"
            );
        }
    }

    private WorkspaceMemberResponse toResponse(WorkspaceMember membership) {
        return new WorkspaceMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getJoinedAt()
        );
    }
}
