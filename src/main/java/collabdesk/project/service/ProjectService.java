package collabdesk.project.service;

import collabdesk.account.AvatarUrlFactory;
import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.entity.AccessRole;
import collabdesk.project.role.entity.ProjectAllowedRole;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.project.role.service.AccessRoleWorkspaceMismatchException;
import collabdesk.user.entity.UserStatus;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Handles project creation and listing. It coordinates workspace authorization,
 * initial access rules and cache invalidation as one use case.
 */
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final AccessRoleRepository accessRoleRepository;
    private final ProjectAllowedRoleRepository projectAllowedRoleRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AvatarUrlFactory avatarUrlFactory;

    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public ProjectService(
            ProjectRepository projectRepository,
            WorkspaceAccessService workspaceAccessService,
            ProjectMemberRepository projectMemberRepository,
            AccessRoleRepository accessRoleRepository,
            ProjectAllowedRoleRepository projectAllowedRoleRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            AvatarUrlFactory avatarUrlFactory,
            WorkspaceProjectAccessChangePublisher accessChangePublisher) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.accessRoleRepository = accessRoleRepository;
        this.projectAllowedRoleRepository = projectAllowedRoleRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.avatarUrlFactory = avatarUrlFactory;
        this.accessChangePublisher = accessChangePublisher;
    }
    /** Creates a project and initializes its access model in one transaction. */
    @Transactional
    public ProjectResponse create(
            Long workspaceId,
            Long currentUserId,
            String name,
            String description,
            Set<Long> requestedRoleIds,
            Set<Long> requestedWorkspaceMemberIds
    ) {
        WorkspaceMember workspaceMember = workspaceAccessService.requireManager(
                workspaceId,
                currentUserId
        );
        Set<Long> roleIds = normalized(requestedRoleIds);
        Set<Long> memberIds = normalized(requestedWorkspaceMemberIds);

        List<AccessRole> roles = roleIds.isEmpty()
                ? List.of()
                : accessRoleRepository.findAllByWorkspace_IdAndIdIn(
                        workspaceId,
                        roleIds
                );
        if (roles.size() != roleIds.size()) {
            throw new AccessRoleWorkspaceMismatchException(
                    "At least one role was not found in this workspace"
            );
        }

        List<WorkspaceMember> selectedMembers = memberIds.isEmpty()
                ? List.of()
                : workspaceMemberRepository.findAllByWorkspace_IdAndIdIn(
                        workspaceId,
                        memberIds
                );
        if (selectedMembers.size() != memberIds.size()
                || selectedMembers.stream().anyMatch(member ->
                        member.getUser().getStatus() == UserStatus.DISABLED)) {
            throw new WorkspaceMemberNotFoundException(
                    "At least one active workspace member was not found"
            );
        }

        Project project =
                new Project(
                workspaceMember.getWorkspace(),
                        name,
                        description,
                        workspaceMember.getUser()
            );
        Project savedProject = projectRepository.saveAndFlush(project);
        projectMemberRepository.save(
                new ProjectMember(savedProject, workspaceMember, false)
        );
        projectMemberRepository.saveAll(selectedMembers.stream()
                .filter(member -> !member.getId().equals(workspaceMember.getId()))
                .filter(member -> member.getRole() != WorkspaceRole.OWNER)
                .map(member -> new ProjectMember(savedProject, member, true))
                .toList());
        projectAllowedRoleRepository.saveAll(roles.stream()
                .map(role -> new ProjectAllowedRole(savedProject, role))
                .toList());
        accessChangePublisher.publish(workspaceId);
        return toResponse(savedProject, isRestricted(savedProject.getId()));
    }
    /** Lists only projects the caller can access inside the workspace. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        WorkspaceMember membership =
                workspaceAccessService.requireMember(workspaceId, currentUserId);
        boolean manager = membership.getRole() == WorkspaceRole.OWNER
                || membership.getRole() == WorkspaceRole.ADMIN;
        return projectRepository.findAccessibleForWorkspace(
                        workspaceId,
                        currentUserId,
                        membership.getId(),
                        manager
                )
                .stream()
                .map(project -> toResponse(project, isRestricted(project.getId())))
                .toList();
    }

    private ProjectResponse toResponse(Project project, boolean restricted) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspace().getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy().getId(),
                project.getCreatedBy().getDisplayName(),
                avatarUrlFactory.create(project.getCreatedBy().getAvatarKey()),
                restricted,
                project.getCreatedAt()
        );
    }

    private boolean isRestricted(Long projectId) {
        return projectMemberRepository.existsByProject_IdAndGrantsAccessTrue(projectId)
                || projectAllowedRoleRepository.existsByProject_Id(projectId);
    }

    private Set<Long> normalized(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>(values);
        if (result.contains(null)) {
            throw new IllegalArgumentException("Access IDs cannot contain null");
        }
        return Set.copyOf(result);
    }
}
