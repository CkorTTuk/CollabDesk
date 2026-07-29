package collabdesk.task.service;

import collabdesk.project.entity.Project;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.repository.TaskRepository;
import collabdesk.taskassignee.repository.TaskAssigneeRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspacemember.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAccessServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAssigneeRepository taskAssigneeRepository;

    private TaskAccessService taskAccessService;

    @BeforeEach
    void setUp() {
        taskAccessService = new TaskAccessService(
                projectAccessService,
                taskRepository,
                taskAssigneeRepository
        );
    }

    @Test
    void projectVisibleTaskIsAccessibleWithoutAssignment() {
        Fixture fixture = fixture(WorkspaceRole.MEMBER, 2L, 2L);
        stubTask(fixture);

        assertSame(
                fixture.task(),
                taskAccessService
                        .requireAccessibleTask(1L, 3L, 4L, 2L)
                        .task()
        );
        verify(
                taskAssigneeRepository,
                never()
        ).existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(4L, 2L);
    }

    @Test
    void assignedUserCanReadAssigneeOnlyTask() {
        Fixture fixture = privateFixture(WorkspaceRole.MEMBER, 2L, 7L);
        stubTask(fixture);
        when(taskAssigneeRepository
                .existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
                        4L,
                        2L
                ))
                .thenReturn(true);

        assertSame(
                fixture.task(),
                taskAccessService
                        .requireAccessibleTask(1L, 3L, 4L, 2L)
                        .task()
        );
    }

    @Test
    void creatorCanReadAssigneeOnlyTaskWithoutAssignment() {
        Fixture fixture = privateFixture(WorkspaceRole.MEMBER, 2L, 2L);
        stubTask(fixture);

        assertSame(
                fixture.task(),
                taskAccessService
                        .requireAccessibleTask(1L, 3L, 4L, 2L)
                        .task()
        );
    }

    @Test
    void managerCanReadAssigneeOnlyTaskWithoutAssignment() {
        Fixture fixture = privateFixture(WorkspaceRole.ADMIN, 2L, 7L);
        stubTask(fixture);

        assertSame(
                fixture.task(),
                taskAccessService
                        .requireAccessibleTask(1L, 3L, 4L, 2L)
                        .task()
        );
    }

    @Test
    void unrelatedProjectMemberCannotReadAssigneeOnlyTask() {
        Fixture fixture = privateFixture(WorkspaceRole.MEMBER, 2L, 7L);
        stubTask(fixture);

        assertThrows(
                TaskNotFoundException.class,
                () -> taskAccessService
                        .requireAccessibleTask(1L, 3L, 4L, 2L)
        );
    }

    @Test
    void viewerCannotWriteEvenWhenTaskIsVisible() {
        Fixture fixture = fixture(WorkspaceRole.VIEWER, 2L, 7L);
        stubTask(fixture);

        assertThrows(
                WorkspaceOperationForbiddenException.class,
                () -> taskAccessService
                        .requireWritableTask(1L, 3L, 4L, 2L)
        );
    }

    private void stubTask(Fixture fixture) {
        when(projectAccessService.requireAccessibleProject(1L, 3L, 2L))
                .thenReturn(fixture.projectAccess());
        when(taskRepository.findByIdAndProject_Id(4L, 3L))
                .thenReturn(Optional.of(fixture.task()));
    }

    private Fixture privateFixture(
            WorkspaceRole role,
            Long currentUserId,
            Long creatorId
    ) {
        Fixture fixture = fixture(role, currentUserId, creatorId);
        fixture.task().changeVisibility(TaskVisibility.ASSIGNEES);
        return fixture;
    }

    private Fixture fixture(
            WorkspaceRole role,
            Long currentUserId,
            Long creatorId
    ) {
        User currentUser = user(currentUserId, "current@test.com");
        User creator = creatorId.equals(currentUserId)
                ? currentUser
                : user(creatorId, "creator@test.com");
        Workspace workspace =
                new Workspace("Task access workspace", null, creator);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceMember membership = role == WorkspaceRole.OWNER
                ? WorkspaceMember.owner(workspace, currentUser)
                : WorkspaceMember.collaborator(workspace, currentUser, role);
        Project project =
                new Project(workspace, "Task access project", null, creator);
        ReflectionTestUtils.setField(project, "id", 3L);
        Task task = new Task(project, "Access task", null, creator);
        ReflectionTestUtils.setField(task, "id", 4L);
        return new Fixture(
                task,
                new AccessibleProject(project, membership)
        );
    }

    private User user(Long id, String email) {
        User user = new User(email, "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private record Fixture(
            Task task,
            AccessibleProject projectAccess
    ) {
    }
}
