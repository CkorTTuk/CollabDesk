package collabdesk.project.role.entity;

import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessRoleTest {

    @Test
    void normalizesNameAndHexColor() {
        User owner = new User("role-owner@test.com", "Role Owner");
        Workspace workspace = new Workspace("Role workspace", null, owner);

        AccessRole role = new AccessRole(
                workspace,
                "  Backend   Developer ",
                "#4f7dF3"
        );

        assertAll(
                () -> assertSame(workspace, role.getWorkspace()),
                () -> assertEquals("Backend Developer", role.getName()),
                () -> assertEquals("#4F7DF3", role.getColor())
        );
    }

    @Test
    void rejectsInvalidValues() {
        User owner = new User("invalid-role@test.com", "Role Owner");
        Workspace workspace = new Workspace("Role workspace", null, owner);

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AccessRole(null, "Developer", "#4F7DF3")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccessRole(workspace, " ", "#4F7DF3")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccessRole(workspace, "Developer", "blue")
                )
        );
    }
}
