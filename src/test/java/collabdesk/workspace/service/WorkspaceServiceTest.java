package collabdesk.workspace.service;

import collabdesk.TestcontainersConfiguration;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.dto.WorkspaceResponse;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkspaceServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        workspaceMemberRepository.deleteAllInBatch();
        workspaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createSavesWorkspaceAndOwnerMembership() {
        User creator = saveUser("creator@test.com", "Creator");

        WorkspaceResponse response = workspaceService.create(
                creator.getId(),
                "  Team workspace  ",
                "  Team description  "
        );

        Workspace savedWorkspace = entityManager.find(Workspace.class, response.id());
        WorkspaceMember savedMembership = workspaceMemberRepository
                .findByWorkspace_IdAndUser_Id(response.id(), creator.getId())
                .orElseThrow();

        assertAll(
                () -> assertNotNull(response.id()),
                () -> assertEquals("Team workspace", response.name()),
                () -> assertEquals("Team description", response.description()),
                () -> assertEquals(WorkspaceRole.OWNER, response.role()),
                () -> assertNotNull(response.createdAt()),
                () -> assertNotNull(savedWorkspace),
                () -> assertEquals(creator.getId(), savedWorkspace.getCreatedBy().getId()),
                () -> assertEquals(WorkspaceRole.OWNER, savedMembership.getRole()),
                () -> assertEquals(creator.getId(), savedMembership.getUser().getId())
        );
    }

    @Test
    void createUsesUserIdPassedToServiceAsCreator() {
        User otherUser = saveUser("other@test.com", "Other");
        User currentUser = saveUser("current@test.com", "Current");

        WorkspaceResponse response = workspaceService.create(
                currentUser.getId(),
                "Current user workspace",
                "Description"
        );

        Workspace savedWorkspace = entityManager.find(Workspace.class, response.id());

        assertAll(
                () -> assertEquals(
                        currentUser.getId(),
                        savedWorkspace.getCreatedBy().getId()
                ),
                () -> assertTrue(
                        workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                                response.id(),
                                currentUser.getId()
                        )
                ),
                () -> assertFalse(
                        workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                                response.id(),
                                otherUser.getId()
                        )
                )
        );
    }

    @Test
    void findForUserReturnsOnlyUsersMembershipsAndUsesMembershipRole() {
        User requestedUser = saveUser("requested@test.com", "Requested");
        User otherUser = saveUser("foreign@test.com", "Foreign");
        Workspace ownedWorkspace = saveWorkspace("Owned workspace", requestedUser);
        Workspace memberWorkspace = saveWorkspace("Member workspace", otherUser);
        Workspace foreignWorkspace = saveWorkspace("Foreign workspace", otherUser);

        workspaceMemberRepository.saveAll(List.of(
                WorkspaceMember.owner(ownedWorkspace, requestedUser),
                WorkspaceMember.member(memberWorkspace, requestedUser),
                WorkspaceMember.owner(foreignWorkspace, otherUser)
        ));

        List<WorkspaceResponse> responses =
                workspaceService.findForUser(requestedUser.getId());

        assertAll(
                () -> assertEquals(2, responses.size()),
                () -> assertEquals(
                        List.of(ownedWorkspace.getId(), memberWorkspace.getId()),
                        responses.stream().map(WorkspaceResponse::id).toList()
                ),
                () -> assertEquals(
                        List.of(WorkspaceRole.OWNER, WorkspaceRole.MEMBER),
                        responses.stream().map(WorkspaceResponse::role).toList()
                ),
                () -> assertFalse(
                        responses.stream().anyMatch(response ->
                                response.id().equals(foreignWorkspace.getId())
                        )
                )
        );
    }

    @Test
    void findForUserReturnsImmutableList() {
        User user = saveUser("immutable@test.com", "Immutable");

        List<WorkspaceResponse> responses =
                workspaceService.findForUser(user.getId());

        assertThrows(
                UnsupportedOperationException.class,
                () -> responses.add(new WorkspaceResponse(
                        1L,
                        "Unexpected",
                        null,
                        WorkspaceRole.OWNER,
                        null
                ))
        );
    }

    private User saveUser(String email, String displayName) {
        return userRepository.save(new User(email, displayName));
    }

    private Workspace saveWorkspace(String name, User creator) {
        return workspaceRepository.save(
                new Workspace(name, "Description", creator)
        );
    }
}
