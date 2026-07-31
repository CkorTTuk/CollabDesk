package collabdesk.project.member.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.project.entity.Project;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectMemberRepositoryTest {

    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void batchLoadsOnlyRequestedProjectsWithIdentityFetchPlan() {
        User owner = userRepository.saveAndFlush(
                new User("overview-repo-owner@test.com", "Overview Owner")
        );
        User memberUser = userRepository.saveAndFlush(
                new User("overview-repo-member@test.com", "Overview Member")
        );
        Workspace workspace = workspaceRepository.saveAndFlush(
                new Workspace("Overview repository workspace", null, owner)
        );
        WorkspaceMember ownerMember = workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.owner(workspace, owner)
        );
        WorkspaceMember member = workspaceMemberRepository.saveAndFlush(
                WorkspaceMember.member(workspace, memberUser)
        );
        Project first = projectRepository.saveAndFlush(
                new Project(workspace, "First overview project", null, owner)
        );
        Project second = projectRepository.saveAndFlush(
                new Project(workspace, "Second overview project", null, owner)
        );
        Project excluded = projectRepository.saveAndFlush(
                new Project(workspace, "Excluded overview project", null, owner)
        );
        projectMemberRepository.saveAllAndFlush(List.of(
                new ProjectMember(first, ownerMember),
                new ProjectMember(first, member),
                new ProjectMember(second, member),
                new ProjectMember(excluded, member)
        ));
        entityManager.clear();

        List<ProjectMember> result = projectMemberRepository
                .findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
                        List.of(first.getId(), second.getId())
                );

        assertAll(
                () -> assertEquals(3, result.size()),
                () -> assertEquals(first.getId(), result.get(0).getProject().getId()),
                () -> assertEquals(first.getId(), result.get(1).getProject().getId()),
                () -> assertEquals(second.getId(), result.get(2).getProject().getId()),
                () -> assertTrue(Hibernate.isInitialized(result.get(0).getProject())),
                () -> assertTrue(Hibernate.isInitialized(result.get(0).getWorkspaceMember())),
                () -> assertTrue(Hibernate.isInitialized(
                        result.get(0).getWorkspaceMember().getUser()
                ))
        );
    }
}
