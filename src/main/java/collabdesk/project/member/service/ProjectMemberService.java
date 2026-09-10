package collabdesk.project.member.service;

import collabdesk.account.AvatarUrlFactory;
import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
import collabdesk.project.role.service.ProjectAllowedRoleService;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.project.member.dto.ProjectMemberResponse;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import collabdesk.workspace.entity.WorkspaceRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Adds and removes project members and replaces their custom access roles while
 * enforcing workspace and project consistency.
 */
@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMemberAccessRoleService memberAccessRoleService;
    private final ProjectAllowedRoleService projectAllowedRoleService;
    private final ProjectPermissionService projectPermissionService;
    private final AvatarUrlFactory avatarUrlFactory;

    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectAccessService projectAccessService,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceMemberAccessRoleService memberAccessRoleService,
            ProjectAllowedRoleService projectAllowedRoleService,
            ProjectPermissionService projectPermissionService,
            AvatarUrlFactory avatarUrlFactory,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectAccessService = projectAccessService;
        this.workspaceAccessService = workspaceAccessService;
        this.memberAccessRoleService = memberAccessRoleService;
        this.projectAllowedRoleService = projectAllowedRoleService;
        this.projectPermissionService = projectPermissionService;
        this.avatarUrlFactory = avatarUrlFactory;
        this.accessChangePublisher = accessChangePublisher;
    }

    /** Lists explicit members of a project after an access check. */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> findAll(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        List<ProjectMember> members = projectMemberRepository
                .findByProject_IdOrderByJoinedAtAsc(projectId);
        return members.stream()
                .map(this::toResponse)
                .toList();
    }

    /** Grants explicit project membership to an existing workspace member. */
    @Transactional
    public ProjectMemberResponse add(
            Long workspaceId,
            Long projectId,
            Long currentUserId,
            Long workspaceMemberId,
            Set<Long> roleIds
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessibleProject access = projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        WorkspaceMember workspaceMember = workspaceMemberRepository
                .findByIdAndWorkspace_Id(workspaceMemberId, workspaceId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "Workspace member was not found"
                ));

        if (workspaceMember.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner project membership cannot be changed"
            );
        }

        try {
            ProjectMember saved = projectMemberRepository
                    .findByProject_IdAndWorkspaceMember_Id(projectId, workspaceMemberId)
                    .map(existing -> {
                        if (existing.isGrantsAccess()) {
                            throw new ProjectMemberAlreadyExistsException(
                                    "Workspace member is already in this project"
                            );
                        }
                        existing.grantAccess();
                        return existing;
                    })
                    .orElseGet(() -> projectMemberRepository.saveAndFlush(
                            new ProjectMember(access.project(), workspaceMember, true)
                    ));
            accessChangePublisher.publish(workspaceId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ProjectMemberAlreadyExistsException(
                    "Workspace member is already in this project"
            );
        }
    }

    /** Atomically replaces the member's custom project roles. */
    @Transactional
    public ProjectMemberResponse replaceRoles(
            Long workspaceId,
            Long projectId,
            Long projectMemberId,
            Long currentUserId,
            Set<Long> roleIds
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        ProjectMember member = projectMemberRepository
                .findByIdAndProject_Id(projectMemberId, projectId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(
                        "Project member was not found"
                ));
        rejectOwner(member);
        memberAccessRoleService.replaceValidated(
                workspaceId,
                member.getWorkspaceMember(),
                roleIds
        );
        projectAllowedRoleService.addAllowed(workspaceId, member.getProject(), roleIds);
        accessChangePublisher.publish(workspaceId);
        return toResponse(member);
    }

    /** Revokes explicit project membership and all dependent role assignments. */
    @Transactional
    public void remove(
            Long workspaceId,
            Long projectId,
            Long projectMemberId,
            Long currentUserId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        ProjectMember member = projectMemberRepository
                .findByIdAndProject_Id(projectMemberId, projectId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(
                        "Project member was not found"
                ));
        rejectOwner(member);
        projectMemberRepository.delete(member);
        accessChangePublisher.publish(workspaceId);
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        WorkspaceMember workspaceMember = member.getWorkspaceMember();
        List<AccessRoleSummaryResponse> roles = memberAccessRoleService
                .findForMember(workspaceMember.getId());
        Set<ProjectPermission> effectivePermissions = projectPermissionService
                .findForMember(member.getProject().getId(), workspaceMember);
        return new ProjectMemberResponse(
                member.getId(),
                workspaceMember.getId(),
                workspaceMember.getUser().getId(),
                workspaceMember.getUser().getEmail(),
                workspaceMember.getUser().getDisplayName(),
                avatarUrlFactory.create(workspaceMember.getUser().getAvatarKey()),
                workspaceMember.getRole(),
                member.getJoinedAt(),
                member.isGrantsAccess(),
                roles,
                effectivePermissions
        );
    }

    private void rejectOwner(ProjectMember member) {
        if (member.getWorkspaceMember().getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerMutationException(
                    "Owner project membership cannot be changed"
            );
        }
    }
}
