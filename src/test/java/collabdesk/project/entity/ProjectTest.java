package collabdesk.project.entity;

import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTest {

    private final User creator = new User("creator@test.com", "Creator");
    private final Workspace workspace =
            new Workspace("Test workspace", "Description", creator);

    @Test
    void requiresWorkspace() {
        assertThrows(
                NullPointerException.class,
                () -> new Project(null, "Project", null, creator)
        );
    }

    @Test
    void requiresCreator() {
        assertThrows(
                NullPointerException.class,
                () -> new Project(workspace, "Project", null, null)
        );
    }

    @Test
    void trimsName() {
        Project project = new Project(workspace, "  Project name  ", null, creator);

        assertEquals("Project name", project.getName());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Project(workspace, "   ", null, creator)
        );
    }

    @Test
    void rejectsNameShorterThanTwoCharactersAfterTrim() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Project(workspace, " x ", null, creator)
        );
    }

    @Test
    void rejectsNameLongerThanOneHundredCharactersAfterTrim() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Project(workspace, " " + "x".repeat(101) + " ", null, creator)
        );
    }

    @Test
    void allowsNullDescription() {
        Project project = new Project(workspace, "Project", null, creator);

        assertNull(project.getDescription());
    }

    @Test
    void convertsBlankDescriptionToNull() {
        Project project = new Project(workspace, "Project", "   ", creator);

        assertNull(project.getDescription());
    }

    @Test
    void rejectsDescriptionLongerThanFiveHundredCharactersAfterTrim() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Project(
                        workspace,
                        "Project",
                        " " + "x".repeat(501) + " ",
                        creator
                )
        );
    }

    @Test
    void createsActiveProjectWithTimestamps() {
        Instant beforeCreation = Instant.now();

        Project project = new Project(
                workspace,
                "Project",
                "  Description  ",
                creator
        );

        Instant afterCreation = Instant.now();

        assertAll(
                () -> assertEquals(workspace, project.getWorkspace()),
                () -> assertEquals(creator, project.getCreatedBy()),
                () -> assertEquals("Description", project.getDescription()),
                () -> assertEquals(ProjectStatus.ACTIVE, project.getStatus()),
                () -> assertNotNull(project.getCreatedAt()),
                () -> assertNotNull(project.getUpdatedAt()),
                () -> assertTrue(
                        !project.getCreatedAt().isBefore(beforeCreation)
                                && !project.getCreatedAt().isAfter(afterCreation)
                ),
                () -> assertTrue(
                        !project.getUpdatedAt().isBefore(beforeCreation)
                                && !project.getUpdatedAt().isAfter(afterCreation)
                )
        );
    }

}
