package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.project.entity.Project;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.role.dto.AccessRoleSummaryResponse;
import collabdesk.project.role.service.ProjectMemberRoleService;
import collabdesk.project.role.service.ProjectMemberRoleSnapshot;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
import collabdesk.project.role.repository.ProjectAllowedRoleRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectAccessOverviewQueryServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectMemberRoleService projectMemberRoleService;
    @Mock private WorkspaceMemberAccessRoleService memberAccessRoleService;
    @Mock private ProjectAllowedRoleRepository projectAllowedRoleRepository;

    private WorkspaceProjectAccessOverviewQueryService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceProjectAccessOverviewQueryService(
                projectAccessService,
                projectMemberRepository,
                memberAccessRoleService,
                projectAllowedRoleRepository,
                new collabdesk.account.AvatarUrlFactory()
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
        ProjectMember firstOwner = projectMember(40L, first, ownerMember, false);
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
        when(memberAccessRoleService.findForMembers(List.of(20L, 21L, 21L)))
                .thenReturn(Map.of(21L, List.of(reviewer)));
        when(projectAllowedRoleRepository.findByProject_IdIn(List.of(30L, 31L)))
                .thenReturn(List.of());

        WorkspaceProjectAccessOverviewResponse result =
                service.findForWorkspace(1L, 10L);

        assertAll(
                () -> assertEquals(2, result.projects().size()),
                () -> assertEquals(
                        "First project",
                        result.projects().get(0).name()
                ),
                () -> assertEquals(
                        first.getCreatedAt(),
                        result.projects().get(0).createdAt()
                ),
                () -> assertEquals(
                        2,
                        result.projects().get(0).members().size()
                ),
                () -> assertEquals(
                        List.of(reviewer),
                        result.projects().get(0).members().get(1).roles()
                ),
                () -> assertEquals(
                        List.of(reviewer),
                        result.projects().get(1).members().get(0).roles()
                ),
                () -> assertFalse(
                        result.projects().get(0).members().get(0).grantsAccess()
                ),
                () -> assertTrue(
                        result.projects().get(0).members().get(1).grantsAccess()
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
                        anyCollection()
                );
        verify(projectMemberRoleService, never()).loadFor(anyCollection());
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
        return projectMember(id, project, workspaceMember, true);
    }

    private ProjectMember projectMember(
            Long id,
            Project project,
            WorkspaceMember workspaceMember,
            boolean grantsAccess
    ) {
        ProjectMember member = new ProjectMember(
                project,
                workspaceMember,
                grantsAccess
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
