package collabdesk.taskassignee.entity;

import collabdesk.project.entity.Project;
import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.task.entity.Task;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspacemember.entity.WorkspaceMember;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TaskAssigneeTest {

    @Test
    void createsAssignmentForSameProjectAndSetsTime() {
        Fixture fixture = fixture();
        Task task = new Task(
                fixture.firstProject(),
                "Assigned task",
                null,
                fixture.user()
        );
        ProjectMember member = new ProjectMember(
                fixture.firstProject(),
                fixture.workspaceMember()
        );

        TaskAssignee assignee = new TaskAssignee(task, member);

        assertAll(
                () -> assertSame(task, assignee.getTask()),
                () -> assertSame(member, assignee.getProjectMember()),
                () -> assertNotNull(assignee.getAssignedAt())
        );
    }

    @Test
    void rejectsMemberFromAnotherProject() {
        Fixture fixture = fixture();
        Task task = new Task(
                fixture.firstProject(),
                "Scoped task",
                null,
                fixture.user()
        );
        ProjectMember otherProjectMember = new ProjectMember(
                fixture.secondProject(),
                fixture.workspaceMember()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskAssignee(task, otherProjectMember)
        );
    }

    private Fixture fixture() {
        User user = new User("task-assignee@test.com", "Task Assignee");
        Workspace workspace = new Workspace("Assignee workspace", null, user);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceMember workspaceMember =
                WorkspaceMember.member(workspace, user);
        Project first = new Project(workspace, "First project", null, user);
        Project second = new Project(workspace, "Second project", null, user);
        ReflectionTestUtils.setField(first, "id", 2L);
        ReflectionTestUtils.setField(second, "id", 3L);
        return new Fixture(user, workspaceMember, first, second);
    }

    private record Fixture(
            User user,
            WorkspaceMember workspaceMember,
            Project firstProject,
            Project secondProject
    ) {
    }
}
