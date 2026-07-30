package collabdesk.project.service;

import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectVisibility;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @BeforeEach
    void setUp() {
        projectAccessService = new ProjectAccessService(
                workspaceAccessService,
                projectRepository,
                projectMemberRepository
        );
    }

    @Test
    void returnsProjectAndMembershipAfterOrderedAccessChecks() {
        User user = new User("access@test.com", "Access User");
        Workspace workspace = new Workspace("Access workspace", null, user);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        Project project = new Project(workspace, "Access project", null, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(membership);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));

        AccessibleProject result =
                projectAccessService.requireAccessibleProject(1L, 3L, 2L);

        InOrder order = inOrder(workspaceAccessService, projectRepository);
        order.verify(workspaceAccessService).requireMember(1L, 2L);
        order.verify(projectRepository).findByIdAndWorkspace_Id(3L, 1L);
        assertAll(
                () -> assertSame(project, result.project()),
                () -> assertSame(membership, result.membership())
        );
    }

    @Test
    void nonMemberDoesNotTriggerProjectQuery() {
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenThrow(new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> projectAccessService
                        .requireAccessibleProject(1L, 3L, 2L)
        );

        verify(
                projectRepository,
                never()
        ).findByIdAndWorkspace_Id(3L, 1L);
    }

    @Test
    void projectOutsideWorkspaceIsRejected() {
        User user = new User("project-scope@test.com", "Scope User");
        Workspace workspace = new Workspace("Scope workspace", null, user);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(membership);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectNotFoundException.class,
                () -> projectAccessService
                        .requireAccessibleProject(1L, 3L, 2L)
        );
    }

    @Test
    void writableAccessUsesContributorPermissionAndScopedProject() {
        User user = new User("write@test.com", "Write User");
        Workspace workspace = new Workspace("Write workspace", null, user);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        Project project = new Project(workspace, "Write project", null, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(membership);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));

        AccessibleProject result =
                projectAccessService.requireWritableProject(1L, 3L, 2L);

        InOrder order = inOrder(workspaceAccessService, projectRepository);
        order.verify(workspaceAccessService).requireMember(1L, 2L);
        order.verify(projectRepository).findByIdAndWorkspace_Id(3L, 1L);
        assertAll(
                () -> assertSame(project, result.project()),
                () -> assertSame(membership, result.membership())
        );
    }

    @Test
    void deniedWritePermissionDoesNotTriggerProjectQuery() {
        User user = new User("viewer@test.com", "Viewer");
        Workspace workspace = new Workspace("Viewer workspace", null, user);
        WorkspaceMember viewer = WorkspaceMember.collaborator(
                workspace,
                user,
                WorkspaceRole.VIEWER
        );
        Project project = new Project(workspace, "Visible project", null, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(viewer);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));

        assertThrows(
                WorkspaceOperationForbiddenException.class,
                () -> projectAccessService
                        .requireWritableProject(1L, 3L, 2L)
        );

        verify(projectRepository).findByIdAndWorkspace_Id(3L, 1L);
    }

    @Test
    void restrictedProjectIsVisibleToProjectMember() {
        User user = user(2L, "restricted-member@test.com");
        Workspace workspace = workspace(user);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        Project project = restrictedProject(workspace, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(membership);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository
                .existsByProject_IdAndWorkspaceMember_User_Id(3L, 2L))
                .thenReturn(true);

        assertSame(
                project,
                projectAccessService
                        .requireAccessibleProject(1L, 3L, 2L)
                        .project()
        );
    }

    @Test
    void restrictedProjectLooksMissingToWorkspaceMemberOutsideProject() {
        User user = user(2L, "restricted-outsider@test.com");
        Workspace workspace = workspace(user);
        WorkspaceMember membership = WorkspaceMember.member(workspace, user);
        Project project = restrictedProject(workspace, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(membership);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));

        assertThrows(
                ProjectNotFoundException.class,
                () -> projectAccessService
                        .requireAccessibleProject(1L, 3L, 2L)
        );
    }

    @Test
    void workspaceManagerCanAccessRestrictedProjectWithoutMembership() {
        User user = user(2L, "restricted-admin@test.com");
        Workspace workspace = workspace(user);
        WorkspaceMember admin = WorkspaceMember.collaborator(
                workspace,
                user,
                WorkspaceRole.ADMIN
        );
        Project project = restrictedProject(workspace, user);
        when(workspaceAccessService.requireMember(1L, 2L))
                .thenReturn(admin);
        when(projectRepository.findByIdAndWorkspace_Id(3L, 1L))
                .thenReturn(Optional.of(project));

        assertSame(
                project,
                projectAccessService
                        .requireAccessibleProject(1L, 3L, 2L)
                        .project()
        );
    }

    private User user(Long id, String email) {
        User user = new User(email, "Access User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Workspace workspace(User user) {
        Workspace workspace = new Workspace("Restricted workspace", null, user);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        return workspace;
    }

    private Project restrictedProject(Workspace workspace, User user) {
        Project project = new Project(workspace, "Restricted project", null, user);
        ReflectionTestUtils.setField(project, "id", 3L);
        project.changeVisibility(ProjectVisibility.RESTRICTED);
        return project;
    }
}
