package collabdesk.project.service;

import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectStatus;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                workspaceAccessService
        );
    }

    @Test
    void memberCreatesProjectForOwnWorkspace() {
        User currentUser = user(7L, "member@test.com");
        Workspace workspace = workspace(11L, currentUser);
        WorkspaceMember membership = WorkspaceMember.member(workspace, currentUser);
        when(workspaceAccessService.requireContributor(11L, 7L))
                .thenReturn(membership);
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> {
                    Project project = invocation.getArgument(0);
                    ReflectionTestUtils.setField(project, "id", 21L);
                    return project;
                });

        ProjectResponse response = projectService.create(
                11L,
                7L,
                "  CollabDesk MVP  ",
                "  First version  "
        );

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project savedProject = captor.getValue();

        assertAll(
                () -> assertEquals(21L, response.id()),
                () -> assertEquals(11L, response.workspaceId()),
                () -> assertEquals("CollabDesk MVP", response.name()),
                () -> assertEquals("First version", response.description()),
                () -> assertEquals(ProjectStatus.ACTIVE, response.status()),
                () -> assertSame(workspace, savedProject.getWorkspace()),
                () -> assertSame(currentUser, savedProject.getCreatedBy())
        );
    }

    @Test
    void nonMemberCannotCreateAndRepositoryIsNotCalled() {
        when(workspaceAccessService.requireContributor(11L, 8L))
                .thenThrow(new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> projectService.create(
                        11L,
                        8L,
                        "Foreign project",
                        null
                )
        );

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void memberReceivesMappedImmutableProjectListAfterAccessCheck() {
        User currentUser = user(7L, "reader@test.com");
        Workspace workspace = workspace(11L, currentUser);
        WorkspaceMember membership = WorkspaceMember.member(workspace, currentUser);
        Project first = project(21L, workspace, currentUser, "First");
        Project second = project(22L, workspace, currentUser, "Second");
        when(workspaceAccessService.requireMember(11L, 7L))
                .thenReturn(membership);
        when(projectRepository.findByWorkspace_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(first, second));

        List<ProjectResponse> responses =
                projectService.findForWorkspace(11L, 7L);

        InOrder callOrder = inOrder(
                workspaceAccessService,
                projectRepository
        );
        callOrder.verify(workspaceAccessService).requireMember(11L, 7L);
        callOrder.verify(projectRepository)
                .findByWorkspace_IdOrderByCreatedAtAsc(11L);

        assertAll(
                () -> assertEquals(
                        List.of(21L, 22L),
                        responses.stream().map(ProjectResponse::id).toList()
                ),
                () -> assertEquals(
                        List.of("First", "Second"),
                        responses.stream().map(ProjectResponse::name).toList()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> responses.add(responses.getFirst())
                )
        );
    }

    @Test
    void nonMemberCannotReadAndProjectQueryIsNotCalled() {
        when(workspaceAccessService.requireMember(11L, 8L))
                .thenThrow(new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> projectService.findForWorkspace(11L, 8L)
        );

        verify(
                projectRepository,
                never()
        ).findByWorkspace_IdOrderByCreatedAtAsc(any());
    }

    private User user(Long id, String email) {
        User user = new User(email, "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Workspace workspace(Long id, User creator) {
        Workspace workspace = new Workspace(
                "Test workspace",
                "Description",
                creator
        );
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
