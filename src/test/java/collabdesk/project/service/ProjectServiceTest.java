package collabdesk.project.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.dto.ProjectResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.entity.ProjectStatus;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.repository.ProjectRepository;
import collabdesk.project.role.repository.AccessRoleRepository;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock ProjectRepository projectRepository;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock AccessRoleRepository accessRoleRepository;
    @Mock ProjectAllowedRoleRepository allowedRoleRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock WorkspaceProjectAccessChangePublisher accessChangePublisher;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(
                projectRepository, workspaceAccessService, projectMemberRepository,
                accessRoleRepository, allowedRoleRepository,
                workspaceMemberRepository, accessChangePublisher
        );
    }

    @Test
    void managerCreatesOpenProjectAndTechnicalMembershipDoesNotRestrictIt() {
        User user = user(7L, "admin@test.com");
        Workspace workspace = workspace(11L, user);
        WorkspaceMember admin = WorkspaceMember.collaborator(workspace, user, WorkspaceRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 12L);
        when(workspaceAccessService.requireManager(11L, 7L)).thenReturn(admin);
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 21L);
            return project;
        });

        ProjectResponse response = service.create(
                11L, 7L, "  CollabDesk MVP  ", "  First version  ", Set.of(), Set.of()
        );

        ArgumentCaptor<ProjectMember> member = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(member.capture());
        assertAll(
                () -> assertEquals(21L, response.id()),
                () -> assertEquals("CollabDesk MVP", response.name()),
                () -> assertEquals("First version", response.description()),
                () -> assertEquals(ProjectStatus.ACTIVE, response.status()),
                () -> assertEquals(7L, response.createdById()),
                () -> assertFalse(response.restricted()),
                () -> assertFalse(member.getValue().isGrantsAccess())
        );
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void memberCannotCreateProject() {
        when(workspaceAccessService.requireManager(11L, 8L))
                .thenThrow(new WorkspaceOperationForbiddenException("Manager required"));
        assertThrows(
                WorkspaceOperationForbiddenException.class,
                () -> service.create(11L, 8L, "Foreign project", null, Set.of(), Set.of())
        );
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsAccessibleProjectsWithCreator() {
        User user = user(7L, "reader@test.com");
        Workspace workspace = workspace(11L, user);
        WorkspaceMember member = WorkspaceMember.member(workspace, user);
        ReflectionTestUtils.setField(member, "id", 17L);
        Project first = project(21L, workspace, user, "First");
        Project second = project(22L, workspace, user, "Second");
        when(workspaceAccessService.requireMember(11L, 7L)).thenReturn(member);
        when(projectRepository.findAccessibleForWorkspace(11L, 7L, 17L, false))
                .thenReturn(List.of(first, second));

        List<ProjectResponse> responses = service.findForWorkspace(11L, 7L);

        assertEquals(List.of(21L, 22L), responses.stream().map(ProjectResponse::id).toList());
        assertEquals(List.of("Test User", "Test User"), responses.stream()
                .map(ProjectResponse::createdByDisplayName).toList());
    }

    private User user(Long id, String email) {
        User user = new User(email, "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Workspace workspace(Long id, User creator) {
        Workspace workspace = new Workspace("Test workspace", "Description", creator);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private Project project(Long id, Workspace workspace, User creator, String name) {
        Project project = new Project(workspace, name, null, creator);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }
}
