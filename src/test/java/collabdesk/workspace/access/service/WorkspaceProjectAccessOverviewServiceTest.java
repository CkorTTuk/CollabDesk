package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.service.ProjectMemberRoleService;
import collabdesk.project.role.service.ProjectMemberRoleSnapshot;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectAccessOverviewServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectMemberRoleService projectMemberRoleService;

    private WorkspaceProjectAccessOverviewService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceProjectAccessOverviewService(
                projectAccessService,
                projectMemberRepository,
                projectMemberRoleService
        );
    }

    @Test
    void returnsProjectsMembersCustomRolesAndDirectAccessInStableOrder() {
        User owner = user(10L, "owner@overview.test", "Owner");
        User memberUser = user(11L, "member@overview.test", "Member");
        Workspace workspace = new Workspace("Overview workspace", null, owner);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceMember ownerMember = WorkspaceMember.owner(workspace, owner);
        WorkspaceMember member = WorkspaceMember.member(workspace, memberUser);
        ReflectionTestUtils.setField(ownerMember, "id", 20L);
        ReflectionTestUtils.setField(member, "id", 21L);

        Project first = project(30L, workspace, owner, "First project");
        Project second = project(31L, workspace, owner, "Second project");
        ProjectMember firstOwner = projectMember(40L, first, ownerMember);
        ProjectMember firstMember = projectMember(41L, first, member);
        ProjectMember secondMember = projectMember(42L, second, member);
        AccessRoleSummaryResponse reviewer = new AccessRoleSummaryResponse(
                50L,
                "Reviewer",
                "#315BDA"
        );

        when(projectAccessService.findAccessibleProjects(1L, 10L))
                .thenReturn(List.of(first, second));
        when(projectMemberRepository
                .findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
                        List.of(30L, 31L)
                ))
                .thenReturn(List.of(firstOwner, firstMember, secondMember));
        when(projectMemberRoleService.loadFor(List.of(40L, 41L, 42L)))
                .thenReturn(new ProjectMemberRoleSnapshot(
                        Map.of(41L, List.of(reviewer)),
                        Map.of(),
                        Set.of(41L)
                ));

        WorkspaceProjectAccessOverviewResponse result =
                service.findForWorkspace(1L, 10L);

        assertAll(
                () -> assertEquals(2, result.projects().size()),
                () -> assertEquals("First project", result.projects().get(0).name()),
                () -> assertEquals(2, result.projects().get(0).members().size()),
                () -> assertEquals(
                        List.of(reviewer),
                        result.projects().get(0).members().get(1).roles()
                ),
                () -> assertTrue(
                        result.projects().get(1).members().get(0).roles().isEmpty()
                )
        );
    }

    @Test
    void emptyWorkspaceDoesNotQueryMembersOrRoles() {
        when(projectAccessService.findAccessibleProjects(1L, 10L))
                .thenReturn(List.of());

        WorkspaceProjectAccessOverviewResponse result =
                service.findForWorkspace(1L, 10L);

        assertTrue(result.projects().isEmpty());
        verify(projectMemberRepository, never())
                .findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
                        org.mockito.ArgumentMatchers.anyCollection()
                );
        verify(projectMemberRoleService, never())
                .loadFor(org.mockito.ArgumentMatchers.anyCollection());
    }

    private User user(Long id, String email, String displayName) {
        User user = new User(email, displayName);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
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

    private ProjectMember projectMember(
            Long id,
            Project project,
            WorkspaceMember workspaceMember
    ) {
        ProjectMember member = new ProjectMember(project, workspaceMember);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
