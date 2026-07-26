package collabdesk.task.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.project.entity.Project;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesTaskWithProjectCreatorAndInitialStatus() {
        User creator = saveUser("task-save@test.com");
        Workspace workspace = saveWorkspace("Task workspace", creator);
        Project project = saveProject("Task project", workspace, creator);

        Task savedTask = taskRepository.saveAndFlush(
                new Task(project, "Persist task", "Description", creator)
        );
        Long taskId = savedTask.getId();
        Long projectId = project.getId();
        Long creatorId = creator.getId();

        entityManager.clear();

        Task foundTask = entityManager.find(Task.class, taskId);

        assertAll(
                () -> assertNotNull(foundTask),
                () -> assertEquals("Persist task", foundTask.getTitle()),
                () -> assertEquals("Description", foundTask.getDescription()),
                () -> assertEquals(projectId, foundTask.getProject().getId()),
                () -> assertEquals(creatorId, foundTask.getCreatedBy().getId()),
                () -> assertEquals(TaskStatus.TODO, foundTask.getStatus()),
                () -> assertNotNull(foundTask.getCreatedAt()),
                () -> assertNotNull(foundTask.getUpdatedAt()),
                () -> assertNotNull(foundTask.getVersion())
        );
    }

    @Test
    void findsOnlyRequestedProjectsTasksInCreatedAtOrder() {
        User creator = saveUser("task-order@test.com");
        Workspace workspace = saveWorkspace("Order workspace", creator);
        Project requestedProject =
                saveProject("Requested project", workspace, creator);
        Project otherProject =
                saveProject("Other project", workspace, creator);

        Task later = new Task(requestedProject, "Later task", null, creator);
        Task earlier = new Task(requestedProject, "Earlier task", null, creator);
        Task foreign = new Task(otherProject, "Foreign task", null, creator);
        ReflectionTestUtils.setField(
                later,
                "createdAt",
                Instant.parse("2026-01-02T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                earlier,
                "createdAt",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        taskRepository.saveAllAndFlush(List.of(later, earlier, foreign));
        Long requestedProjectId = requestedProject.getId();

        entityManager.clear();

        List<Task> tasks =
                taskRepository.findByProject_IdOrderByCreatedAtAsc(
                        requestedProjectId
                );

        assertAll(
                () -> assertEquals(
                        List.of("Earlier task", "Later task"),
                        tasks.stream().map(Task::getTitle).toList()
                ),
                () -> assertTrue(tasks.stream().allMatch(task ->
                        task.getProject().getId().equals(requestedProjectId)
                )),
                () -> assertFalse(
                        taskRepository
                                .findByIdAndProject_Id(
                                        foreign.getId(),
                                        requestedProjectId
                                )
                                .isPresent()
                ),
                () -> assertTrue(
                        taskRepository
                                .findByIdAndProject_Id(
                                        earlier.getId(),
                                        requestedProjectId
                                )
                                .isPresent()
                )
        );
    }

    @Test
    void deletingProjectCascadeDeletesItsTasks() {
        User creator = saveUser("task-cascade@test.com");
        Workspace workspace = saveWorkspace("Cascade workspace", creator);
        Project project = saveProject("Cascade project", workspace, creator);
        Task task = taskRepository.saveAndFlush(
                new Task(project, "Cascade task", null, creator)
        );
        Long projectId = project.getId();
        Long taskId = task.getId();

        entityManager.clear();

        Project projectToDelete = entityManager.find(Project.class, projectId);
        projectRepository.delete(projectToDelete);
        projectRepository.flush();
        entityManager.clear();

        assertAll(
                () -> assertFalse(taskRepository.existsById(taskId)),
                () -> assertTrue(
                        taskRepository
                                .findByProject_IdOrderByCreatedAtAsc(projectId)
                                .isEmpty()
                )
        );
    }

    @Test
    void statusUpdateIsPersistedAndIncrementsVersion() {
        User creator = saveUser("task-version@test.com");
        Workspace workspace = saveWorkspace("Version workspace", creator);
        Project project = saveProject("Version project", workspace, creator);
        Task task = taskRepository.saveAndFlush(
                new Task(project, "Version task", null, creator)
        );
        Long taskId = task.getId();
        Long initialVersion = task.getVersion();

        task.changeStatus(TaskStatus.DONE);
        taskRepository.flush();
        Long updatedVersion = task.getVersion();
        entityManager.clear();

        Task reloadedTask = taskRepository.findById(taskId).orElseThrow();

        assertAll(
                () -> assertEquals(TaskStatus.DONE, reloadedTask.getStatus()),
                () -> assertTrue(updatedVersion > initialVersion),
                () -> assertEquals(updatedVersion, reloadedTask.getVersion())
        );
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User(email, "Task User"));
    }

    private Workspace saveWorkspace(String name, User creator) {
        return workspaceRepository.saveAndFlush(
                new Workspace(name, null, creator)
        );
    }

    private Project saveProject(
            String name,
            Workspace workspace,
            User creator
    ) {
        return projectRepository.saveAndFlush(
                new Project(workspace, name, null, creator)
        );
    }
}
