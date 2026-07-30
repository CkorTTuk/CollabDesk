package collabdesk.task.service;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.project.entity.Project;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.project.service.ProjectNotFoundException;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.repository.TaskRepository;
import collabdesk.task.assignee.repository.TaskAssigneeRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private TaskAssigneeRepository taskAssigneeRepository;

    @Mock
    private TaskAccessService taskAccessService;

    @Mock
    private ProjectPermissionService projectPermissionService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository,
                projectAccessService,
                taskAssigneeRepository,
                new TaskResponseMapper(),
                taskAccessService,
                projectPermissionService
        );
    }

    @Test
    void memberCreatesNormalizedTodoTaskForAccessibleProject() {
        TestAccess testAccess = access();
        when(projectAccessService.requireAccessibleProject(1L, 2L, 3L))
                .thenReturn(testAccess.accessibleProject());
        when(projectPermissionService.findEffectiveProjectPermissions(
                testAccess.accessibleProject(),
                3L
        )).thenReturn(Set.of(ProjectPermission.CREATE_TASK));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> {
                    Task task = invocation.getArgument(0);
                    ReflectionTestUtils.setField(task, "id", 4L);
                    return task;
                });

        TaskResponse response = taskService.create(
                1L,
                2L,
                3L,
                "  Implement tasks  ",
                "  Build task board  "
        );

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task savedTask = captor.getValue();
        assertAll(
                () -> assertEquals(4L, response.id()),
                () -> assertEquals(2L, response.projectId()),
                () -> assertEquals("Implement tasks", response.title()),
                () -> assertEquals("Build task board", response.description()),
                () -> assertEquals(TaskStatus.TODO, response.status()),
                () -> assertEquals(3L, response.createdById()),
                () -> assertSame(
                        testAccess.project(),
                        savedTask.getProject()
                ),
                () -> assertSame(testAccess.user(), savedTask.getCreatedBy())
        );
    }

    @Test
    void accessDeniedCreateDoesNotCallRepository() {
        when(projectAccessService.requireAccessibleProject(1L, 2L, 3L))
                .thenThrow(new ProjectNotFoundException(
                        "Project was not found"
                ));

        assertThrows(
                ProjectNotFoundException.class,
                () -> taskService.create(
                        1L,
                        2L,
                        3L,
                        "Forbidden task",
                        null
                )
        );

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void listChecksAccessBeforeQueryAndReturnsImmutableResponses() {
        TestAccess testAccess = access();
        Task first = task(4L, testAccess, "First task");
        Task second = task(5L, testAccess, "Second task");
        when(projectAccessService.requireAccessibleProject(1L, 2L, 3L))
                .thenReturn(testAccess.accessibleProject());
        when(projectPermissionService.findEffectiveProjectPermissions(
                testAccess.accessibleProject(),
                3L
        )).thenReturn(Set.of());
        when(taskRepository.findAccessibleForProject(2L, 3L, false))
                .thenReturn(List.of(first, second));
        when(taskAssigneeRepository.findByTask_IdIn(
                List.of(4L, 5L)
        ))
                .thenReturn(List.of());

        List<TaskResponse> responses =
                taskService.findForProject(1L, 2L, 3L);

        InOrder order = inOrder(projectAccessService, taskRepository);
        order.verify(projectAccessService)
                .requireAccessibleProject(1L, 2L, 3L);
        order.verify(taskRepository)
                .findAccessibleForProject(2L, 3L, false);
        assertAll(
                () -> assertEquals(
                        List.of(4L, 5L),
                        responses.stream().map(TaskResponse::id).toList()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> responses.add(responses.getFirst())
                )
        );
    }

    @Test
    void accessDeniedListDoesNotQueryTasks() {
        when(projectAccessService.requireAccessibleProject(1L, 2L, 3L))
                .thenThrow(new ProjectNotFoundException(
                        "Project was not found"
                ));

        assertThrows(
                ProjectNotFoundException.class,
                () -> taskService.findForProject(1L, 2L, 3L)
        );

        verify(
                taskRepository,
                never()
        ).findAccessibleForProject(2L, 3L, false);
    }

    @Test
    void changesStatusOnlyOnTaskScopedToProject() {
        TestAccess testAccess = access();
        Task task = task(4L, testAccess, "Status task");
        AccessibleTask accessibleTask = new AccessibleTask(
                task,
                testAccess.accessibleProject()
        );
        when(taskAccessService.requireAccessibleTask(1L, 2L, 4L, 3L))
                .thenReturn(accessibleTask);
        when(projectPermissionService.findEffectiveTaskPermissions(
                accessibleTask,
                3L
        )).thenReturn(Set.of(ProjectPermission.CHANGE_TASK_STATUS));
        when(taskAssigneeRepository
                .existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
                        4L,
                        3L
                ))
                .thenReturn(true);
        when(taskAssigneeRepository.findByTask_Id(4L))
                .thenReturn(Optional.empty());

        TaskResponse response = taskService.changeStatus(
                1L,
                2L,
                4L,
                3L,
                TaskStatus.DONE
        );

        assertAll(
                () -> assertEquals(TaskStatus.DONE, task.getStatus()),
                () -> assertEquals(TaskStatus.DONE, response.status())
        );
    }

    @Test
    void taskOutsideProjectIsNotChanged() {
        TestAccess testAccess = access();
        when(taskAccessService.requireAccessibleTask(1L, 2L, 99L, 3L))
                .thenThrow(new TaskNotFoundException(
                        "Task was not found"
                ));

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.changeStatus(
                        1L,
                        2L,
                        99L,
                        3L,
                        TaskStatus.DONE
                )
        );
    }

    @Test
    void creatorCanRestrictAssignedTask() {
        TestAccess testAccess = access();
        Task task = task(4L, testAccess, "Private task");
        when(taskAccessService.requireAccessibleTask(1L, 2L, 4L, 3L))
                .thenReturn(new AccessibleTask(
                        task,
                        testAccess.accessibleProject()
                ));
        when(taskAssigneeRepository.existsByTask_Id(4L))
                .thenReturn(true);
        when(taskAssigneeRepository.findByTask_Id(4L))
                .thenReturn(Optional.empty());

        TaskResponse response = taskService.changeVisibility(
                1L,
                2L,
                4L,
                3L,
                TaskVisibility.ASSIGNEES
        );

        assertEquals(TaskVisibility.ASSIGNEES, response.visibility());
    }

    @Test
    void assigneeOnlyVisibilityRequiresAtLeastOneAssignee() {
        TestAccess testAccess = access();
        Task task = task(4L, testAccess, "Unassigned private task");
        when(taskAccessService.requireAccessibleTask(1L, 2L, 4L, 3L))
                .thenReturn(new AccessibleTask(
                        task,
                        testAccess.accessibleProject()
                ));

        assertThrows(
                TaskVisibilityConflictException.class,
                () -> taskService.changeVisibility(
                        1L,
                        2L,
                        4L,
                        3L,
                        TaskVisibility.ASSIGNEES
                )
        );
        assertEquals(TaskVisibility.PROJECT, task.getVisibility());
    }

    private TestAccess access() {
        User user = new User("task-service@test.com", "Task User");
        ReflectionTestUtils.setField(user, "id", 3L);
        Workspace workspace = new Workspace("Task workspace", null, user);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        Project project = new Project(workspace, "Task project", null, user);
        ReflectionTestUtils.setField(project, "id", 2L);
        return new TestAccess(
                user,
                project,
                new AccessibleProject(project, membership)
        );
    }

    private Task task(Long id, TestAccess testAccess, String title) {
        Task task = new Task(
                testAccess.project(),
                title,
                null,
                testAccess.user()
        );
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private record TestAccess(
            User user,
            Project project,
            AccessibleProject accessibleProject
    ) {
    }
}
