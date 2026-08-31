package collabdesk.workspace.member.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceMemberAlreadyExistsException;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import collabdesk.workspace.service.exceptions.WorkspaceUserNotFoundException;
import collabdesk.workspace.member.dto.WorkspaceMemberResponse;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WorkspaceMemberService {
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserRepository userRepository;
    private final WorkspaceMemberAccessRoleService memberAccessRoleService;

    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;
    public WorkspaceMemberService(
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAccessService workspaceAccessService,
            UserRepository userRepository,
            WorkspaceMemberAccessRoleService memberAccessRoleService,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userRepository = userRepository;
        this.memberAccessRoleService = memberAccessRoleService;
        this.accessChangePublisher = accessChangePublisher;
    }
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);

        List<WorkspaceMember> members = workspaceMemberRepository
                .findByWorkspace_IdOrderByJoinedAtAsc(workspaceId);
        Map<Long, List<AccessRoleSummaryResponse>> roles = memberAccessRoleService
                .findForMembers(members.stream().map(WorkspaceMember::getId).toList());
        return members.stream()
                .map(member -> toResponse(
                        member,
                        roles.getOrDefault(member.getId(), List.of())
                ))
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
                workspaceAccessService.requireManager(workspaceId, currentUserId);

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

        return toResponse(workspaceMemberRepository.save(membership), List.of());
    }

    @Transactional
    public WorkspaceMemberResponse changeRole(
            Long workspaceId,
            Long memberId,
            Long currentUserId,
            WorkspaceRole newRole
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        rejectOwnerRole(newRole);

        WorkspaceMember membership = findScopedMember(workspaceId, memberId);
        if (membership.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner role cannot be changed"
            );
        }

        membership.changeRole(newRole);
        accessChangePublisher.publish(workspaceId);
        return toResponse(
                membership,
                memberAccessRoleService.findForMember(membership.getId())
        );
    }

    @Transactional
    public void remove(
            Long workspaceId,
            Long memberId,
            Long currentUserId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);

        WorkspaceMember membership = findScopedMember(workspaceId, memberId);
        if (membership.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner membership cannot be removed"
            );
        }

        workspaceMemberRepository.delete(membership);
        accessChangePublisher.publish(workspaceId);
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

    private WorkspaceMemberResponse toResponse(
            WorkspaceMember membership,
            List<AccessRoleSummaryResponse> accessRoles
    ) {
        return new WorkspaceMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getJoinedAt(),
                List.copyOf(accessRoles)
        );
    }
}
