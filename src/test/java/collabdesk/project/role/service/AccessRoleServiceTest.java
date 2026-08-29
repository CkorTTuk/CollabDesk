package collabdesk.project.role.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.role.dto.AccessRoleResponse;
import collabdesk.project.role.dto.UpdateAccessRoleRequest;
import collabdesk.project.role.entity.AccessRole;
import collabdesk.project.role.repository.AccessRolePermissionRepository;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.ProjectMemberRoleRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRoleServiceTest {

    @Mock
    private AccessRoleRepository accessRoleRepository;

    @Mock
    private AccessRolePermissionRepository permissionRepository;

    @Mock
    private ProjectMemberRoleRepository projectMemberRoleRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private WorkspaceProjectAccessChangePublisher accessChangePublisher;

    private AccessRoleService service;
    private AccessRole role;

    @BeforeEach
    void setUp() {
        service = new AccessRoleService(
                accessRoleRepository,
                permissionRepository,
                projectMemberRoleRepository,
                workspaceAccessService,
                accessChangePublisher
        );

        User owner = new User("owner@role.test", "Owner");
        Workspace workspace = new Workspace("Workspace", null, owner);
        ReflectionTestUtils.setField(workspace, "id", 11L);
        role = new AccessRole(workspace, "Reviewer", "#315BDA");
        ReflectionTestUtils.setField(role, "id", 50L);
        ReflectionTestUtils.setField(role, "version", 0L);
    }

    @Test
    void updatePublishesInvalidationForChangedWorkspace() {
        UpdateAccessRoleRequest request = new UpdateAccessRoleRequest(
                "Editor",
                "#112233",
                Set.of()
        );
        when(accessRoleRepository.findByIdAndWorkspace_Id(50L, 11L))
                .thenReturn(Optional.of(role));

        AccessRoleResponse response = service.update(11L, 50L, 7L, request);

        assertAll(
                () -> assertEquals("Editor", response.name()),
                () -> assertEquals("#112233", response.color())
        );
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void missingRoleDoesNotPublishInvalidation() {
        when(accessRoleRepository.findByIdAndWorkspace_Id(99L, 11L))
                .thenReturn(Optional.empty());
        UpdateAccessRoleRequest request = new UpdateAccessRoleRequest(
                "Editor",
                "#112233",
                Set.of()
        );

        assertThrows(
                AccessRoleNotFoundException.class,
                () -> service.update(11L, 99L, 7L, request)
        );

        verify(permissionRepository, never()).saveAll(any());
        verify(accessChangePublisher, never()).publish(any());
    }
}
