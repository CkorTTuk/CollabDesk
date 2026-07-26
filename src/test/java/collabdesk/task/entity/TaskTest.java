package collabdesk.task.entity;

import collabdesk.project.entity.Project;
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

class TaskTest {

    private final User creator = new User("task-creator@test.com", "Creator");
    private final Workspace workspace =
            new Workspace("Task workspace", null, creator);
    private final Project project =
            new Project(workspace, "Task project", null, creator);

    @Test
    void requiresProject() {
        assertThrows(
                NullPointerException.class,
                () -> new Task(null, "Task title", null, creator)
        );
    }

    @Test
    void requiresCreator() {
        assertThrows(
                NullPointerException.class,
                () -> new Task(project, "Task title", null, null)
        );
    }

    @Test
    void normalizesTitleAndDescription() {
        Task task = new Task(
                project,
                "  Implement board  ",
                "  Add three columns  ",
                creator
        );

        assertAll(
                () -> assertEquals("Implement board", task.getTitle()),
                () -> assertEquals("Add three columns", task.getDescription())
        );
    }

    @Test
    void rejectsNullBlankAndTooShortTitle() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Task(project, null, null, creator)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Task(project, "   ", null, creator)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Task(project, " x ", null, creator)
                )
        );
    }

    @Test
    void rejectsTitleLongerThanOneHundredFiftyCharactersAfterTrim() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Task(
                        project,
                        " " + "x".repeat(151) + " ",
                        null,
                        creator
                )
        );
    }

    @Test
    void allowsNullAndConvertsBlankDescriptionToNull() {
        Task nullDescription =
                new Task(project, "First task", null, creator);
        Task blankDescription =
                new Task(project, "Second task", "   ", creator);

        assertAll(
                () -> assertNull(nullDescription.getDescription()),
                () -> assertNull(blankDescription.getDescription())
        );
    }

    @Test
    void rejectsDescriptionLongerThanOneThousandCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Task(
                        project,
                        "Task title",
                        " " + "x".repeat(1001) + " ",
                        creator
                )
        );
    }

    @Test
    void createsTodoTaskWithTimestamps() {
        Instant beforeCreation = Instant.now();

        Task task = new Task(project, "Task title", null, creator);

        Instant afterCreation = Instant.now();

        assertAll(
                () -> assertEquals(TaskStatus.TODO, task.getStatus()),
                () -> assertNotNull(task.getCreatedAt()),
                () -> assertNotNull(task.getUpdatedAt()),
                () -> assertTrue(
                        !task.getCreatedAt().isBefore(beforeCreation)
                                && !task.getCreatedAt().isAfter(afterCreation)
                ),
                () -> assertEquals(task.getCreatedAt(), task.getUpdatedAt())
        );
    }

    @Test
    void changesStatusAndUpdatesTimestamp() {
        Task task = new Task(project, "Task title", null, creator);
        Instant previousUpdatedAt = task.getUpdatedAt();

        task.changeStatus(TaskStatus.IN_PROGRESS);

        assertAll(
                () -> assertEquals(TaskStatus.IN_PROGRESS, task.getStatus()),
                () -> assertTrue(
                        !task.getUpdatedAt().isBefore(previousUpdatedAt)
                )
        );
    }

    @Test
    void rejectsNullStatus() {
        Task task = new Task(project, "Task title", null, creator);

        assertThrows(
                NullPointerException.class,
                () -> task.changeStatus(null)
        );
    }
}
