package collabdesk.workspace.service;

import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspacemember.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAccessServiceTest {

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    private WorkspaceAccessService workspaceAccessService;
    private User user;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceAccessService =
                new WorkspaceAccessService(workspaceMemberRepository);
        user = new User("member@test.com", "Member");
        workspace = new Workspace("Workspace", null, user);
    }

    @Test
    void ownerPassesRequireOwner() {
        WorkspaceMember owner = WorkspaceMember.owner(workspace, user);
        membershipExists(owner);

        assertSame(owner, workspaceAccessService.requireOwner(11L, 7L));
    }

    @Test
    void nonOwnerRolesDoNotPassRequireOwner() {
        for (WorkspaceRole role : new WorkspaceRole[]{
                WorkspaceRole.ADMIN,
                WorkspaceRole.MEMBER,
                WorkspaceRole.VIEWER
        }) {
            membershipExists(
                    WorkspaceMember.collaborator(workspace, user, role)
            );

            assertThrows(
                    WorkspaceOperationForbiddenException.class,
                    () -> workspaceAccessService.requireOwner(11L, 7L)
            );
        }
    }

    @Test
    void ownerAdminAndMemberPassRequireContributor() {
        for (WorkspaceRole role : new WorkspaceRole[]{
                WorkspaceRole.OWNER,
                WorkspaceRole.ADMIN,
                WorkspaceRole.MEMBER
        }) {
            WorkspaceMember membership = role == WorkspaceRole.OWNER
                    ? WorkspaceMember.owner(workspace, user)
                    : WorkspaceMember.collaborator(workspace, user, role);
            membershipExists(membership);

            assertDoesNotThrow(
                    () -> workspaceAccessService.requireContributor(11L, 7L)
            );
        }
    }

    @Test
    void viewerDoesNotPassRequireContributor() {
        membershipExists(
                WorkspaceMember.collaborator(
                        workspace,
                        user,
                        WorkspaceRole.VIEWER
                )
        );

        assertThrows(
                WorkspaceOperationForbiddenException.class,
                () -> workspaceAccessService.requireContributor(11L, 7L)
        );
    }

    @Test
    void ownerAndAdminPassRequireManager() {
        for (WorkspaceRole role : new WorkspaceRole[]{
                WorkspaceRole.OWNER,
                WorkspaceRole.ADMIN
        }) {
            WorkspaceMember membership = role == WorkspaceRole.OWNER
                    ? WorkspaceMember.owner(workspace, user)
                    : WorkspaceMember.collaborator(workspace, user, role);
            membershipExists(membership);

            assertSame(
                    membership,
                    workspaceAccessService.requireManager(11L, 7L)
            );
        }
    }

    @Test
    void memberAndViewerDoNotPassRequireManager() {
        for (WorkspaceRole role : new WorkspaceRole[]{
                WorkspaceRole.MEMBER,
                WorkspaceRole.VIEWER
        }) {
            membershipExists(
                    WorkspaceMember.collaborator(workspace, user, role)
            );

            assertThrows(
                    WorkspaceOperationForbiddenException.class,
                    () -> workspaceAccessService.requireManager(11L, 7L)
            );
        }
    }

    @Test
    void nonMemberReceivesNeutralAccessException() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_Id(11L, 7L))
                .thenReturn(Optional.empty());

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> workspaceAccessService.requireMember(11L, 7L)
        );
    }

    private void membershipExists(WorkspaceMember membership) {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_Id(11L, 7L))
                .thenReturn(Optional.of(membership));
    }
}
