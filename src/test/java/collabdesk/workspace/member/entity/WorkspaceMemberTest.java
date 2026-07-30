package collabdesk.workspace.member.entity;

import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceMemberTest {

    private final User user = new User("member@test.com", "Member");
    private final Workspace workspace =
            new Workspace("Test workspace", null, user);

    @Test
    void collaboratorAcceptsNonOwnerRoles() {
        List<WorkspaceMember> memberships = List.of(
                WorkspaceMember.collaborator(workspace, user, WorkspaceRole.ADMIN),
                WorkspaceMember.collaborator(workspace, user, WorkspaceRole.MEMBER),
                WorkspaceMember.collaborator(workspace, user, WorkspaceRole.VIEWER)
        );

        assertAll(
                () -> assertEquals(
                        List.of(
                                WorkspaceRole.ADMIN,
                                WorkspaceRole.MEMBER,
                                WorkspaceRole.VIEWER
                        ),
                        memberships.stream().map(WorkspaceMember::getRole).toList()
                ),
                () -> memberships.forEach(membership ->
                        assertNotNull(membership.getJoinedAt())
                )
        );
    }

    @Test
    void collaboratorRejectsOwnerRole() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkspaceMember.collaborator(
                        workspace,
                        user,
                        WorkspaceRole.OWNER
                )
        );
    }

    @Test
    void changeRoleChangesMemberToViewer() {
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);

        membership.changeRole(WorkspaceRole.VIEWER);

        assertEquals(WorkspaceRole.VIEWER, membership.getRole());
    }

    @Test
    void changeRoleRejectsNull() {
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);

        assertThrows(
                NullPointerException.class,
                () -> membership.changeRole(null)
        );
    }

    @Test
    void ownerRoleCannotBeChanged() {
        WorkspaceMember membership = WorkspaceMember.owner(workspace, user);

        assertThrows(
                IllegalStateException.class,
                () -> membership.changeRole(WorkspaceRole.ADMIN)
        );
        assertEquals(WorkspaceRole.OWNER, membership.getRole());
    }

    @Test
    void ownerRoleCannotBeAssignedToCollaborator() {
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);

        assertThrows(
                IllegalArgumentException.class,
                () -> membership.changeRole(WorkspaceRole.OWNER)
        );
        assertEquals(WorkspaceRole.MEMBER, membership.getRole());
    }
}
