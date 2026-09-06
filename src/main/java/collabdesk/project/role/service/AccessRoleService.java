package collabdesk.project.role.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.*;
import collabdesk.project.role.entity.AccessRole;
import collabdesk.project.role.entity.AccessRolePermission;
import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.repository.AccessRolePermissionRepository;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.ProjectMemberRoleRepository;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.project.role.repository.WorkspaceMemberAccessRoleRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages workspace-defined access roles and their permission sets. It prevents
 * duplicate names and deletion of roles that are still assigned.
 */
@Service
public class AccessRoleService {

    private final AccessRoleRepository accessRoleRepository;
    private final AccessRolePermissionRepository permissionRepository;
    private final ProjectMemberRoleRepository projectMemberRoleRepository;
    private final WorkspaceMemberAccessRoleRepository memberAccessRoleRepository;
    private final ProjectAllowedRoleRepository projectAllowedRoleRepository;
    private final WorkspaceAccessService workspaceAccessService;

    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public AccessRoleService(
            AccessRoleRepository accessRoleRepository,
            AccessRolePermissionRepository permissionRepository,
            ProjectMemberRoleRepository projectMemberRoleRepository,
            WorkspaceMemberAccessRoleRepository memberAccessRoleRepository,
            ProjectAllowedRoleRepository projectAllowedRoleRepository,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.accessRoleRepository = accessRoleRepository;
        this.permissionRepository = permissionRepository;
        this.projectMemberRoleRepository = projectMemberRoleRepository;
        this.memberAccessRoleRepository = memberAccessRoleRepository;
        this.projectAllowedRoleRepository = projectAllowedRoleRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.accessChangePublisher = accessChangePublisher;
    }

    /** Lists custom roles defined by the workspace. */
    @Transactional(readOnly = true)
    public List<AccessRoleResponse> findAll(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);
        List<AccessRole> roles =
                accessRoleRepository.findByWorkspace_IdOrderByNameAsc(
                        workspaceId
                );
        Map<Long, Set<ProjectPermission>> permissions =
                permissionsByRole(roles.stream().map(AccessRole::getId).toList());
        return roles.stream()
                .map(role -> toResponse(
                        role,
                        permissions.getOrDefault(role.getId(), Set.of())
                ))
                .toList();
    }

    /** Creates a named role and its permission set. */
    @Transactional
    public AccessRoleResponse create(
            Long workspaceId,
            Long currentUserId,
            CreateAccessRoleRequest request
    ) {
        Workspace workspace = workspaceAccessService
                .requireManager(workspaceId, currentUserId)
                .getWorkspace();
        if (accessRoleRepository.existsByWorkspace_IdAndNameIgnoreCase(
                workspaceId,
                request.name().trim()
        )) {
            throw duplicate();
        }

        try {
            AccessRole role = accessRoleRepository.saveAndFlush(
                    new AccessRole(workspace, request.name(), request.color())
            );
            savePermissions(role, request.permissions());
            return toResponse(role, request.permissions());
        } catch (DataIntegrityViolationException ex) {
            throw duplicate();
        }
    }

    /** Replaces a role's editable data and permissions. */
    @Transactional
    public AccessRoleResponse update(
            Long workspaceId,
            Long roleId,
            Long currentUserId,
            UpdateAccessRoleRequest request
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessRole role = requireRole(workspaceId, roleId);
        if (accessRoleRepository
                .existsByWorkspace_IdAndNameIgnoreCaseAndIdNot(
                        workspaceId,
                        request.name().trim(),
                        roleId
                )) {
            throw duplicate();
        }

        try {
            role.update(request.name(), request.color());

            permissionRepository.deleteByRole_Id(roleId);
            permissionRepository.flush();

            savePermissions(role, request.permissions());
            accessRoleRepository.flush();

            accessChangePublisher.publish(workspaceId);

            return toResponse(role, request.permissions());
        } catch (DataIntegrityViolationException ex) {
            throw duplicate();
        }
    }

    /** Deletes an unused role after checking workspace ownership. */
    @Transactional
    public void delete(
            Long workspaceId,
            Long roleId,
            Long currentUserId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessRole role = requireRole(workspaceId, roleId);
        if (projectMemberRoleRepository.existsByRole_Id(roleId)
                || memberAccessRoleRepository.existsByRole_Id(roleId)
                || projectAllowedRoleRepository.existsByRole_Id(roleId)) {
            throw new AccessRoleInUseException(
                    "Role is assigned to at least one project member"
            );
        }
        accessRoleRepository.delete(role);
    }

    private AccessRole requireRole(Long workspaceId, Long roleId) {
        return accessRoleRepository.findByIdAndWorkspace_Id(roleId, workspaceId)
                .orElseThrow(() -> new AccessRoleNotFoundException(
                        "Access role was not found"
                ));
    }

    private void savePermissions(
            AccessRole role,
            Set<ProjectPermission> permissions
    ) {
        permissionRepository.saveAll(
                permissions.stream()
                        .map(permission ->
                                new AccessRolePermission(role, permission))
                        .toList()
        );
    }

    private Map<Long, Set<ProjectPermission>> permissionsByRole(
            Collection<Long> roleIds
    ) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return permissionRepository.findByRole_IdIn(roleIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getRole().getId(),
                        Collectors.mapping(
                                AccessRolePermission::getPermission,
                                Collectors.toCollection(
                                        () -> EnumSet.noneOf(
                                                ProjectPermission.class
                                        )
                                )
                        )
                ));
    }

    private AccessRoleResponse toResponse(
            AccessRole role,
            Set<ProjectPermission> permissions
    ) {
        return new AccessRoleResponse(
                role.getId(),
                role.getWorkspace().getId(),
                role.getName(),
                role.getColor(),
                Set.copyOf(permissions),
                role.getVersion()
        );
    }

    private AccessRoleAlreadyExistsException duplicate() {
        return new AccessRoleAlreadyExistsException(
                "A role with this name already exists in the workspace"
        );
    }
}
