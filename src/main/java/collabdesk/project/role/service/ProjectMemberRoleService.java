package collabdesk.project.role.service;

import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.entity.*;
import collabdesk.project.role.repository.*;
import collabdesk.project.member.entity.ProjectMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Replaces and loads custom roles attached to a project member. Replacement is
 * transactional so callers never observe a partially updated role set.
 */
@Service
public class ProjectMemberRoleService {

    private final AccessRoleRepository accessRoleRepository;
    private final AccessRolePermissionRepository permissionRepository;
    private final ProjectMemberRoleRepository projectMemberRoleRepository;

    public ProjectMemberRoleService(
            AccessRoleRepository accessRoleRepository,
            AccessRolePermissionRepository permissionRepository,
            ProjectMemberRoleRepository projectMemberRoleRepository
    ) {
        this.accessRoleRepository = accessRoleRepository;
        this.permissionRepository = permissionRepository;
        this.projectMemberRoleRepository = projectMemberRoleRepository;
    }

    /** Replaces all custom roles assigned to one explicit project member. */
    @Transactional
    public void replace(
            ProjectMember projectMember,
            Long workspaceId,
            Set<Long> roleIds
    ) {
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

        projectMemberRoleRepository.deleteByProjectMember_Id(
                projectMember.getId()
        );
        projectMemberRoleRepository.flush();
        projectMemberRoleRepository.saveAll(
                roles.stream()
                        .map(role -> new ProjectMemberRole(projectMember, role))
                        .toList()
        );
    }

    /** Loads role IDs and effective permissions used by authorization checks. */
    @Transactional(readOnly = true)
    public ProjectMemberRoleSnapshot loadFor(
            Collection<Long> projectMemberIds
    ) {
        if (projectMemberIds.isEmpty()) {
            return ProjectMemberRoleSnapshot.empty();
        }
        List<ProjectMemberRole> assignments =
                projectMemberRoleRepository.findByProjectMember_IdIn(
                        projectMemberIds
                );
        if (assignments.isEmpty()) {
            return ProjectMemberRoleSnapshot.empty();
        }

        Set<Long> roleIds = assignments.stream()
                .map(assignment -> assignment.getRole().getId())
                .collect(Collectors.toSet());
        Map<Long, Set<ProjectPermission>> permissionsByRole =
                permissionRepository.findByRole_IdIn(roleIds)
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

        Map<Long, List<AccessRoleSummaryResponse>> roles = assignments.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getProjectMember().getId(),
                        Collectors.mapping(
                                item -> new AccessRoleSummaryResponse(
                                        item.getRole().getId(),
                                        item.getRole().getName(),
                                        item.getRole().getColor()
                                ),
                                Collectors.toList()
                        )
                ));
        roles.values().forEach(list -> list.sort(
                Comparator.comparing(AccessRoleSummaryResponse::name)
        ));

        Map<Long, Set<ProjectPermission>> permissions = new HashMap<>();
        assignments.forEach(assignment -> permissions
                .computeIfAbsent(
                        assignment.getProjectMember().getId(),
                        ignored -> EnumSet.noneOf(ProjectPermission.class)
                )
                .addAll(permissionsByRole.getOrDefault(
                        assignment.getRole().getId(),
                        Set.of()
                )));

        return new ProjectMemberRoleSnapshot(
                Map.copyOf(roles),
                permissions.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue())
                )),
                assignments.stream()
                        .map(item -> item.getProjectMember().getId())
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
