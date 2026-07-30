package collabdesk.project.member.entity;

import collabdesk.project.entity.Project;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberTest {

    @Test
    void createsMembershipForSameWorkspaceAndSetsJoinTime() {
        User user = new User("project-member@test.com", "Project Member");
        Workspace workspace = workspace(1L, user, "First workspace");
        Project project = project(2L, workspace, user, "First project");
        WorkspaceMember workspaceMember =
                WorkspaceMember.member(workspace, user);

        ProjectMember member = new ProjectMember(project, workspaceMember);

        assertAll(
                () -> assertSame(project, member.getProject()),
                () -> assertSame(workspaceMember, member.getWorkspaceMember()),
                () -> assertNotNull(member.getJoinedAt())
        );
    }

    @Test
    void rejectsWorkspaceMemberFromAnotherWorkspace() {
        User user = new User("wrong-project-member@test.com", "Wrong Member");
        Workspace projectWorkspace = workspace(1L, user, "Project workspace");
        Workspace otherWorkspace = workspace(2L, user, "Other workspace");
        Project project = project(
                3L,
                projectWorkspace,
                user,
                "Scoped project"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectMember(
                        project,
                        WorkspaceMember.member(otherWorkspace, user)
                )
        );
    }

    private Workspace workspace(Long id, User creator, String name) {
        Workspace workspace = new Workspace(name, null, creator);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private Project project(
            Long id,
            Workspace workspace,
            User creator,
            String name
    ) {
        Project project = new Project(workspace, name, null, creator);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }
}
