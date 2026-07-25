package collabdesk.workspace.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkspaceRepositoryTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesWorkspaceWithCreator() {
        User creator = saveUser("creator@test.com", "Creator");

        Workspace savedWorkspace = workspaceRepository.saveAndFlush(
                new Workspace("Team workspace", "Team description", creator)
        );
        Long workspaceId = savedWorkspace.getId();
        Long creatorId = creator.getId();

        entityManager.clear();

        Workspace foundWorkspace = entityManager.find(Workspace.class, workspaceId);

        assertAll(
                () -> assertNotNull(foundWorkspace),
                () -> assertEquals("Team workspace", foundWorkspace.getName()),
                () -> assertEquals("Team description", foundWorkspace.getDescription()),
                () -> assertEquals(creatorId, foundWorkspace.getCreatedBy().getId()),
                () -> assertNotNull(foundWorkspace.getCreatedAt()),
                () -> assertNotNull(foundWorkspace.getUpdatedAt()),
                () -> assertNotNull(foundWorkspace.getVersion())
        );
    }

    @Test
    void savesMembershipWithRole() {
        User user = saveUser("owner@test.com", "Owner");
        Workspace workspace = saveWorkspace("Owner workspace", user);

        WorkspaceMember savedMembership = workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.owner(workspace, user)
        );
        Long membershipId = savedMembership.getId();
        Long workspaceId = workspace.getId();
        Long userId = user.getId();

        entityManager.clear();

        WorkspaceMember foundMembership = entityManager.find(
                WorkspaceMember.class,
                membershipId
        );

        assertAll(
                () -> assertNotNull(foundMembership),
                () -> assertEquals(workspaceId, foundMembership.getWorkspace().getId()),
                () -> assertEquals(userId, foundMembership.getUser().getId()),
                () -> assertEquals(WorkspaceRole.OWNER, foundMembership.getRole()),
                () -> assertNotNull(foundMembership.getJoinedAt())
        );
    }

    @Test
    void rejectsDuplicateWorkspaceAndUserMembership() {
        User user = saveUser("duplicate@test.com", "Duplicate");
        Workspace workspace = saveWorkspace("Duplicate workspace", user);
        workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.owner(workspace, user)
        );

        WorkspaceMember duplicate = WorkspaceMember.owner(workspace, user);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> workspaceMemberRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void findsOnlyMembershipsOfRequestedUser() {
        User firstUser = saveUser("first@test.com", "First");
        User secondUser = saveUser("second@test.com", "Second");
        Workspace firstWorkspace = saveWorkspace("First workspace", firstUser);
        Workspace secondWorkspace = saveWorkspace("Second workspace", firstUser);
        Workspace foreignWorkspace = saveWorkspace("Foreign workspace", secondUser);

        workspaceMemberRepository.saveAllAndFlush(List.of(
                WorkspaceMember.owner(firstWorkspace, firstUser),
                WorkspaceMember.owner(secondWorkspace, firstUser),
                WorkspaceMember.owner(foreignWorkspace, secondUser)
        ));

        entityManager.clear();

        List<WorkspaceMember> memberships =
                workspaceMemberRepository.findByUser_IdOrderByWorkspace_CreatedAtAsc(
                        firstUser.getId()
                );

        assertAll(
                () -> assertEquals(2, memberships.size()),
                () -> assertEquals(
                        List.of(firstWorkspace.getId(), secondWorkspace.getId()),
                        memberships.stream()
                                .map(membership -> membership.getWorkspace().getId())
                                .toList()
                ),
                () -> assertTrue(
                        memberships.stream()
                                .allMatch(membership ->
                                        membership.getUser().getId().equals(firstUser.getId())
                                )
                )
        );
    }

    @Test
    void findsMembershipByWorkspaceIdAndUserId() {
        User member = saveUser("member@test.com", "Member");
        User otherUser = saveUser("other@test.com", "Other");
        Workspace workspace = saveWorkspace("Membership lookup", member);
        workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.owner(workspace, member)
        );
        Long workspaceId = workspace.getId();
        Long memberId = member.getId();

        entityManager.clear();

        WorkspaceMember foundMembership = workspaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, memberId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(workspaceId, foundMembership.getWorkspace().getId()),
                () -> assertEquals(memberId, foundMembership.getUser().getId()),
                () -> assertTrue(
                        workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                                workspaceId,
                                memberId
                        )
                ),
                () -> assertFalse(
                        workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                                workspaceId,
                                otherUser.getId()
                        )
                )
        );
    }

    @Test
    void deletesMembershipsWhenWorkspaceIsDeleted() {
        User user = saveUser("cascade@test.com", "Cascade");
        Workspace workspace = saveWorkspace("Cascade workspace", user);
        workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.owner(workspace, user)
        );
        Long workspaceId = workspace.getId();
        Long userId = user.getId();

        entityManager.clear();

        Workspace workspaceToDelete = entityManager.find(Workspace.class, workspaceId);
        workspaceRepository.delete(workspaceToDelete);
        workspaceRepository.flush();
        entityManager.clear();

        assertAll(
                () -> assertFalse(
                        workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(
                                workspaceId,
                                userId
                        )
                ),
                () -> assertEquals(0, workspaceMemberRepository.count())
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
