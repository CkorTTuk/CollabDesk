package collabdesk.project.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectStatus;
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
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesProjectWithWorkspaceCreatorAndStatus() {
        User creator = saveUser("creator@test.com", "Creator");
        Workspace workspace = saveWorkspace("Team workspace", creator);

        Project savedProject = projectRepository.saveAndFlush(
                new Project(workspace, "CollabDesk MVP", "First version", creator)
        );
        Long projectId = savedProject.getId();
        Long workspaceId = workspace.getId();
        Long creatorId = creator.getId();

        entityManager.clear();

        Project foundProject = entityManager.find(Project.class, projectId);

        assertAll(
                () -> assertNotNull(foundProject),
                () -> assertEquals("CollabDesk MVP", foundProject.getName()),
                () -> assertEquals("First version", foundProject.getDescription()),
                () -> assertEquals(workspaceId, foundProject.getWorkspace().getId()),
                () -> assertEquals(creatorId, foundProject.getCreatedBy().getId()),
                () -> assertEquals(ProjectStatus.ACTIVE, foundProject.getStatus()),
                () -> assertNotNull(foundProject.getCreatedAt()),
                () -> assertNotNull(foundProject.getUpdatedAt()),
                () -> assertNotNull(foundProject.getVersion())
        );
    }

    @Test
    void findsOnlyProjectsOfRequestedWorkspaceOrderedByCreatedAt() {
        User creator = saveUser("projects@test.com", "Creator");
        Workspace requestedWorkspace = saveWorkspace("Requested workspace", creator);
        Workspace otherWorkspace = saveWorkspace("Other workspace", creator);

        Project laterProject =
                new Project(requestedWorkspace, "Later project", null, creator);
        Project earlierProject =
                new Project(requestedWorkspace, "Earlier project", null, creator);
        Project foreignProject =
                new Project(otherWorkspace, "Foreign project", null, creator);

        ReflectionTestUtils.setField(
                laterProject,
                "createdAt",
                Instant.parse("2026-01-02T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                earlierProject,
                "createdAt",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        projectRepository.saveAllAndFlush(
                List.of(laterProject, earlierProject, foreignProject)
        );
        Long requestedWorkspaceId = requestedWorkspace.getId();

        entityManager.clear();

        List<Project> projects =
                projectRepository.findByWorkspace_IdOrderByCreatedAtAsc(
                        requestedWorkspaceId
                );

        assertAll(
                () -> assertEquals(2, projects.size()),
                () -> assertEquals(
                        List.of("Earlier project", "Later project"),
                        projects.stream().map(Project::getName).toList()
                ),
                () -> assertTrue(
                        projects.stream().allMatch(project ->
                                project.getWorkspace().getId().equals(requestedWorkspaceId)
                        )
                )
        );
    }

    @Test
    void deletesProjectsWhenWorkspaceIsDeleted() {
        User creator = saveUser("cascade-project@test.com", "Creator");
        Workspace workspace = saveWorkspace("Cascade workspace", creator);
        Project project = projectRepository.saveAndFlush(
                new Project(workspace, "Cascade project", null, creator)
        );
        Long workspaceId = workspace.getId();
        Long projectId = project.getId();

        entityManager.clear();

        Workspace workspaceToDelete = entityManager.find(Workspace.class, workspaceId);
        workspaceRepository.delete(workspaceToDelete);
        workspaceRepository.flush();
        entityManager.clear();

        assertAll(
                () -> assertFalse(projectRepository.existsById(projectId)),
                () -> assertEquals(
                        0,
                        projectRepository
                                .findByWorkspace_IdOrderByCreatedAtAsc(workspaceId)
                                .size()
                )
        );
    }

    private User saveUser(String email, String displayName) {
        return userRepository.saveAndFlush(new User(email, displayName));
    }

    private Workspace saveWorkspace(String name, User creator) {
        return workspaceRepository.saveAndFlush(
                new Workspace(name, "Description", creator)
        );
    }
}
