package collabdesk.project.role.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.AccessRole;
import collabdesk.project.role.entity.ProjectAllowedRole;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class ProjectAllowedRoleService {
    private final ProjectAllowedRoleRepository allowedRoleRepository;
    private final AccessRoleRepository accessRoleRepository;
    private final ProjectAccessService projectAccessService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public ProjectAllowedRoleService(
            ProjectAllowedRoleRepository allowedRoleRepository,
            AccessRoleRepository accessRoleRepository,
            ProjectAccessService projectAccessService,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.allowedRoleRepository = allowedRoleRepository;
        this.accessRoleRepository = accessRoleRepository;
        this.projectAccessService = projectAccessService;
        this.workspaceAccessService = workspaceAccessService;
        this.accessChangePublisher = accessChangePublisher;
    }

    @Transactional(readOnly = true)
    public List<AccessRoleSummaryResponse> findAll(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        projectAccessService.requireAccessibleProject(workspaceId, projectId, currentUserId);
        return responses(projectId);
    }

    @Transactional
    public List<AccessRoleSummaryResponse> replace(
            Long workspaceId,
            Long projectId,
            Long currentUserId,
            Set<Long> requestedRoleIds
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessibleProject access = projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        Set<Long> roleIds = requestedRoleIds == null ? Set.of() : Set.copyOf(requestedRoleIds);
        List<AccessRole> roles = roleIds.isEmpty()
                ? List.of()
                : accessRoleRepository.findAllByWorkspace_IdAndIdIn(workspaceId, roleIds);
        if (roles.size() != roleIds.size()) {
            throw new AccessRoleWorkspaceMismatchException(
                    "At least one role was not found in this workspace"
            );
        }
        allowedRoleRepository.deleteByProject_Id(projectId);
        allowedRoleRepository.flush();
        allowedRoleRepository.saveAll(roles.stream()
                .map(role -> new ProjectAllowedRole(access.project(), role))
                .toList());
        accessChangePublisher.publish(workspaceId);
        return responses(projectId);
    }

    @Transactional
    public void addAllowed(
            Long workspaceId,
            collabdesk.project.entity.Project project,
            Set<Long> requestedRoleIds
    ) {
        Set<Long> roleIds = requestedRoleIds == null ? Set.of() : Set.copyOf(requestedRoleIds);
        if (roleIds.isEmpty()) {
            return;
        }
        List<AccessRole> roles = accessRoleRepository
                .findAllByWorkspace_IdAndIdIn(workspaceId, roleIds);
        if (roles.size() != roleIds.size()) {
            throw new AccessRoleWorkspaceMismatchException(
                    "At least one role was not found in this workspace"
            );
        }
        Set<Long> existing = allowedRoleRepository
                .findByProject_IdOrderByRole_NameAsc(project.getId())
                .stream().map(item -> item.getRole().getId()).collect(java.util.stream.Collectors.toSet());
        allowedRoleRepository.saveAll(roles.stream()
                .filter(role -> !existing.contains(role.getId()))
                .map(role -> new ProjectAllowedRole(project, role))
                .toList());
    }

    private List<AccessRoleSummaryResponse> responses(Long projectId) {
        return allowedRoleRepository.findByProject_IdOrderByRole_NameAsc(projectId)
                .stream()
                .map(item -> new AccessRoleSummaryResponse(
                        item.getRole().getId(),
                        item.getRole().getName(),
                        item.getRole().getColor()
                ))
                .toList();
    }
}
