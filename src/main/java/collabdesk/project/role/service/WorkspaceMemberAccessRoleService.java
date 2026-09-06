package collabdesk.project.role.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.AccessRole;
import collabdesk.project.role.entity.WorkspaceMemberAccessRole;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.WorkspaceMemberAccessRoleRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assigns custom access roles to workspace members. It validates that every
 * assigned role belongs to the same workspace before replacing associations.
 */
@Service
public class WorkspaceMemberAccessRoleService {
    private final WorkspaceMemberAccessRoleRepository assignmentRepository;
    private final AccessRoleRepository accessRoleRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public WorkspaceMemberAccessRoleService(
            WorkspaceMemberAccessRoleRepository assignmentRepository,
            AccessRoleRepository accessRoleRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.assignmentRepository = assignmentRepository;
        this.accessRoleRepository = accessRoleRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.accessChangePublisher = accessChangePublisher;
    }

    /** Validates and replaces all custom access roles of a workspace member. */
    @Transactional
    public List<AccessRoleSummaryResponse> replace(
            Long workspaceId,
            Long workspaceMemberId,
            Long currentUserId,
            Set<Long> requestedRoleIds
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        WorkspaceMember member = workspaceMemberRepository
                .findByIdAndWorkspace_Id(workspaceMemberId, workspaceId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "Workspace member was not found"
                ));
        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner custom roles cannot be changed"
            );
        }
        replaceValidated(workspaceId, member, requestedRoleIds);
        accessChangePublisher.publish(workspaceId);
        return findForMember(member.getId());
    }

    /** Internal replacement entry point for callers that already checked access. */
    @Transactional
    public void replaceValidated(
            Long workspaceId,
            WorkspaceMember member,
            Set<Long> requestedRoleIds
    ) {
        Set<Long> roleIds = requestedRoleIds == null ? Set.of() : Set.copyOf(requestedRoleIds);
        List<AccessRole> roles = roleIds.isEmpty()
                ? List.of()
                : accessRoleRepository.findAllByWorkspace_IdAndIdIn(workspaceId, roleIds);
        if (roles.size() != roleIds.size()) {
            throw new AccessRoleWorkspaceMismatchException(
                    "At least one role was not found in this workspace"
            );
        }
        assignmentRepository.deleteByWorkspaceMember_Id(member.getId());
        assignmentRepository.flush();
        assignmentRepository.saveAll(roles.stream()
                .map(role -> new WorkspaceMemberAccessRole(member, role))
                .toList());
    }

    /** Lists custom access roles attached to one workspace member. */
    @Transactional(readOnly = true)
    public List<AccessRoleSummaryResponse> findForMember(Long memberId) {
        return assignmentRepository.findByWorkspaceMember_Id(memberId).stream()
                .map(item -> summary(item.getRole()))
                .sorted(Comparator.comparing(AccessRoleSummaryResponse::name))
                .toList();
    }

    /** Batch-loads role summaries to avoid per-member queries in overview screens. */
    @Transactional(readOnly = true)
    public Map<Long, List<AccessRoleSummaryResponse>> findForMembers(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return assignmentRepository.findByWorkspaceMember_IdIn(memberIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getWorkspaceMember().getId(),
                        Collectors.mapping(item -> summary(item.getRole()), Collectors.toList())
                ));
    }

    private AccessRoleSummaryResponse summary(AccessRole role) {
        return new AccessRoleSummaryResponse(role.getId(), role.getName(), role.getColor());
    }
}
