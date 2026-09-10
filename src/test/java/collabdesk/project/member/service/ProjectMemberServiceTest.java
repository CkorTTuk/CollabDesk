package collabdesk.project.member.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.entity.Project;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.service.ProjectMemberRoleService;
import collabdesk.project.role.service.ProjectMemberRoleSnapshot;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
import collabdesk.project.role.service.ProjectAllowedRoleService;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private ProjectMemberRoleService projectMemberRoleService;

    @Mock private WorkspaceMemberAccessRoleService memberAccessRoleService;
    @Mock private ProjectAllowedRoleService projectAllowedRoleService;
    @Mock private ProjectPermissionService projectPermissionService;

    @Mock
    private WorkspaceProjectAccessChangePublisher accessChangePublisher;

    private ProjectMemberService service;
    private Workspace workspace;
    private WorkspaceMember ownerMembership;
    private WorkspaceMember memberMembership;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberService(
                projectMemberRepository,
                workspaceMemberRepository,
                projectAccessService,
                workspaceAccessService,
                memberAccessRoleService,
                projectAllowedRoleService,
                projectPermissionService,
                new collabdesk.account.AvatarUrlFactory(),
                accessChangePublisher
        );

        User owner = user(7L, "owner@project-member.test");
        User member = user(8L, "member@project-member.test");
        workspace = new Workspace("Workspace", null, owner);
        ReflectionTestUtils.setField(workspace, "id", 11L);
        ownerMembership = WorkspaceMember.owner(workspace, owner);
        memberMembership = WorkspaceMember.member(workspace, member);
        ReflectionTestUtils.setField(ownerMembership, "id", 20L);
        ReflectionTestUtils.setField(memberMembership, "id", 21L);
        project = new Project(workspace, "Project", null, owner);
        ReflectionTestUtils.setField(project, "id", 30L);
        org.mockito.Mockito.lenient()
                .when(memberAccessRoleService.findForMember(any()))
                .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient()
                .when(projectPermissionService.findForMember(any(), any()))
                .thenReturn(Set.of());
    }

    @Test
    void addPublishesInvalidationForChangedWorkspace() {
        when(projectAccessService.requireAccessibleProject(11L, 30L, 7L))
                .thenReturn(new AccessibleProject(project, ownerMembership));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(21L, 11L))
                .thenReturn(Optional.of(memberMembership));
        when(projectMemberRepository.saveAndFlush(any(ProjectMember.class)))
                .thenAnswer(invocation -> {
                    ProjectMember saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 40L);
                    return saved;
                });
        service.add(11L, 30L, 7L, 21L, Set.of());

        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void replaceRolesPublishesInvalidationForChangedWorkspace() {
        ProjectMember projectMember = projectMember(40L);
        when(projectMemberRepository.findByIdAndProject_Id(40L, 30L))
                .thenReturn(Optional.of(projectMember));
        service.replaceRoles(11L, 30L, 40L, 7L, Set.of(50L));

        verify(memberAccessRoleService).replaceValidated(
                11L,
                memberMembership,
                Set.of(50L)
        );
        verify(projectAllowedRoleService).addAllowed(11L, project, Set.of(50L));
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void removePublishesInvalidationForChangedWorkspace() {
        ProjectMember projectMember = projectMember(40L);
        when(projectMemberRepository.findByIdAndProject_Id(40L, 30L))
                .thenReturn(Optional.of(projectMember));

        service.remove(11L, 30L, 40L, 7L);

        verify(projectMemberRepository).delete(projectMember);
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void missingMemberDoesNotPublishInvalidation() {
        when(projectMemberRepository.findByIdAndProject_Id(99L, 30L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectMemberNotFoundException.class,
                () -> service.remove(11L, 30L, 99L, 7L)
        );

        verify(projectMemberRepository, never()).delete(any());
        verify(accessChangePublisher, never()).publish(any());
    }

    private ProjectMember projectMember(Long id) {
        ProjectMember projectMember = new ProjectMember(
                project,
                memberMembership
        );
        ReflectionTestUtils.setField(projectMember, "id", id);
        return projectMember;
    }

    private ProjectMemberRoleSnapshot emptySnapshot() {
        return new ProjectMemberRoleSnapshot(Map.of(), Map.of(), Set.of());
    }

    private User user(Long id, String email) {
        User user = new User(email, "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
