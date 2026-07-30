package collabdesk.project.member.service;

import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.service.ProjectMemberRoleService;
import collabdesk.project.role.service.ProjectMemberRoleSnapshot;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectMemberRoleService projectMemberRoleService;

    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectAccessService projectAccessService,
            WorkspaceAccessService workspaceAccessService,
            ProjectMemberRoleService projectMemberRoleService
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectAccessService = projectAccessService;
        this.workspaceAccessService = workspaceAccessService;
        this.projectMemberRoleService = projectMemberRoleService;
    }

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
        ProjectMemberRoleSnapshot snapshot = projectMemberRoleService.loadFor(
                members.stream().map(ProjectMember::getId).toList()
        );
        return members.stream()
                .map(member -> toResponse(member, snapshot))
                .toList();
    }

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

        if (projectMemberRepository.existsByProject_IdAndWorkspaceMember_Id(
                projectId,
                workspaceMemberId
        )) {
            throw new ProjectMemberAlreadyExistsException(
                    "Workspace member is already in this project"
            );
        }

        try {
            ProjectMember saved = projectMemberRepository.saveAndFlush(
                    new ProjectMember(access.project(), workspaceMember)
            );
            projectMemberRoleService.replace(saved, workspaceId, roleIds);
            return toResponse(
                    saved,
                    projectMemberRoleService.loadFor(Set.of(saved.getId()))
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ProjectMemberAlreadyExistsException(
                    "Workspace member is already in this project"
            );
        }
    }

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
        projectMemberRoleService.replace(member, workspaceId, roleIds);
        return toResponse(
                member,
                projectMemberRoleService.loadFor(Set.of(member.getId()))
        );
    }

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
        projectMemberRepository.delete(member);
    }

    private ProjectMemberResponse toResponse(
            ProjectMember member,
            ProjectMemberRoleSnapshot snapshot
    ) {
        WorkspaceMember workspaceMember = member.getWorkspaceMember();
        List<AccessRoleSummaryResponse> roles = snapshot.roles()
                .getOrDefault(member.getId(), List.of());
        Set<ProjectPermission> effectivePermissions;
        switch (workspaceMember.getRole()) {
            case OWNER, ADMIN -> effectivePermissions =
                    Set.copyOf(ProjectPermissionService.BASE_MEMBER_PERMISSIONS);
            case VIEWER -> effectivePermissions = Set.of();
            case MEMBER -> effectivePermissions =
                    snapshot.membersWithCustomRoles().contains(member.getId())
                            ? snapshot.permissions().getOrDefault(
                                    member.getId(),
                                    Set.of()
                            )
                            : ProjectPermissionService.BASE_MEMBER_PERMISSIONS;
            default -> throw new IllegalStateException(
                    "Unsupported workspace role"
            );
        }
        return new ProjectMemberResponse(
                member.getId(),
                workspaceMember.getId(),
                workspaceMember.getUser().getId(),
                workspaceMember.getUser().getEmail(),
                workspaceMember.getUser().getDisplayName(),
                workspaceMember.getRole(),
                member.getJoinedAt(),
                roles,
                effectivePermissions
        );
    }
}
