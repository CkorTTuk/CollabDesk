package collabdesk.workspace.member.service;

import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceMemberAlreadyExistsException;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import collabdesk.workspace.service.exceptions.WorkspaceUserNotFoundException;
import collabdesk.workspace.member.dto.WorkspaceMemberResponse;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.project.role.service.WorkspaceMemberAccessRoleService;
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
class WorkspaceMemberServiceTest {

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceProjectAccessChangePublisher accessChangePublisher;
    @Mock private WorkspaceMemberAccessRoleService memberAccessRoleService;

    private WorkspaceMemberService workspaceMemberService;
    private User owner;
    private Workspace workspace;
    private WorkspaceMember ownerMembership;

    @BeforeEach
    void setUp() {
        workspaceMemberService = new WorkspaceMemberService(
                workspaceMemberRepository,
                workspaceAccessService,
                userRepository,
                memberAccessRoleService,
                new collabdesk.account.AvatarUrlFactory(),
                accessChangePublisher
        );
        owner = user(7L, "owner@test.com", "Owner");
        workspace = workspace(11L, owner);
        ownerMembership = membership(
                21L,
                WorkspaceMember.owner(workspace, owner)
        );
        org.mockito.Mockito.lenient()
                .when(memberAccessRoleService.findForMembers(any()))
                .thenReturn(java.util.Map.of());
    }

    @Test
    void findForWorkspaceChecksAccessAndReturnsImmutableMappedList() {
        User member = user(8L, "member@test.com", "Member");
        member.changeAvatar("member-avatar.jpg");
        WorkspaceMember memberMembership = membership(
                22L,
                WorkspaceMember.member(workspace, member)
        );
        when(workspaceAccessService.requireMember(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByWorkspace_IdOrderByJoinedAtAsc(11L))
                .thenReturn(List.of(ownerMembership, memberMembership));

        List<WorkspaceMemberResponse> result =
                workspaceMemberService.findForWorkspace(11L, 7L);

        InOrder order = inOrder(
                workspaceAccessService,
                workspaceMemberRepository
        );
        order.verify(workspaceAccessService).requireMember(11L, 7L);
        order.verify(workspaceMemberRepository)
                .findByWorkspace_IdOrderByJoinedAtAsc(11L);

        assertAll(
                () -> assertEquals(List.of(21L, 22L),
                        result.stream().map(WorkspaceMemberResponse::id).toList()),
                () -> assertEquals(List.of("owner@test.com", "member@test.com"),
                        result.stream().map(WorkspaceMemberResponse::email).toList()),
                () -> assertEquals("/api/v1/avatars/member-avatar.jpg",
                        result.get(1).avatarUrl()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.add(result.getFirst())
                )
        );
    }

    @Test
    void findForWorkspaceDoesNotQueryMembersWhenAccessIsDenied() {
        when(workspaceAccessService.requireMember(11L, 9L))
                .thenThrow(new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> workspaceMemberService.findForWorkspace(11L, 9L)
        );
        verify(workspaceMemberRepository, never())
                .findByWorkspace_IdOrderByJoinedAtAsc(any());
    }

    @Test
    void addNormalizesEmailAndSavesCollaboratorInOwnersWorkspace() {
        User invitedUser = user(8L, "member@test.com", "Member");
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(userRepository.findByEmail("member@test.com"))
                .thenReturn(Optional.of(invitedUser));
        when(workspaceMemberRepository
                .existsByWorkspace_IdAndUser_Id(11L, 8L))
                .thenReturn(false);
        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> membership(
                        22L,
                        invocation.getArgument(0)
                ));

        WorkspaceMemberResponse response = workspaceMemberService.add(
                11L,
                7L,
                "  MEMBER@TEST.COM ",
                WorkspaceRole.ADMIN
        );

        ArgumentCaptor<WorkspaceMember> captor =
                ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberRepository).save(captor.capture());
        WorkspaceMember saved = captor.getValue();

        assertAll(
                () -> assertEquals(22L, response.id()),
                () -> assertEquals(8L, response.userId()),
                () -> assertEquals(WorkspaceRole.ADMIN, response.role()),
                () -> assertSame(workspace, saved.getWorkspace()),
                () -> assertSame(invitedUser, saved.getUser())
        );
    }

    @Test
    void addRejectsOwnerRoleBeforeUserLookup() {
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);

        assertThrows(
                WorkspaceOwnerMutationException.class,
                () -> workspaceMemberService.add(
                        11L,
                        7L,
                        "member@test.com",
                        WorkspaceRole.OWNER
                )
        );
        verify(userRepository, never()).findByEmail(any());
        verify(workspaceMemberRepository, never()).save(any());
        verify(accessChangePublisher, never()).publish(any());
    }

    @Test
    void addHidesMissingAndDisabledUsersBehindSameException() {
        User disabled = user(8L, "disabled@test.com", "Disabled");
        disabled.disable();
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(userRepository.findByEmail("missing@test.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("disabled@test.com"))
                .thenReturn(Optional.of(disabled));

        assertAll(
                () -> assertThrows(
                        WorkspaceUserNotFoundException.class,
                        () -> workspaceMemberService.add(
                                11L,
                                7L,
                                "missing@test.com",
                                WorkspaceRole.MEMBER
                        )
                ),
                () -> assertThrows(
                        WorkspaceUserNotFoundException.class,
                        () -> workspaceMemberService.add(
                                11L,
                                7L,
                                "disabled@test.com",
                                WorkspaceRole.MEMBER
                        )
                )
        );
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    void addRejectsDuplicateMembership() {
        User member = user(8L, "member@test.com", "Member");
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(userRepository.findByEmail("member@test.com"))
                .thenReturn(Optional.of(member));
        when(workspaceMemberRepository
                .existsByWorkspace_IdAndUser_Id(11L, 8L))
                .thenReturn(true);

        assertThrows(
                WorkspaceMemberAlreadyExistsException.class,
                () -> workspaceMemberService.add(
                        11L,
                        7L,
                        "member@test.com",
                        WorkspaceRole.MEMBER
                )
        );
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    void changeRoleUsesScopedLookupAndDirtyChecking() {
        User member = user(8L, "member@test.com", "Member");
        WorkspaceMember membership = membership(
                22L,
                WorkspaceMember.member(workspace, member)
        );
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(22L, 11L))
                .thenReturn(Optional.of(membership));

        WorkspaceMemberResponse response = workspaceMemberService.changeRole(
                11L,
                22L,
                7L,
                WorkspaceRole.VIEWER
        );

        assertAll(
                () -> assertEquals(WorkspaceRole.VIEWER, membership.getRole()),
                () -> assertEquals(WorkspaceRole.VIEWER, response.role())
        );
        verify(workspaceMemberRepository, never()).save(any());
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void changeRoleReturnsNotFoundForMemberFromAnotherWorkspace() {
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(99L, 11L))
                .thenReturn(Optional.empty());

        assertThrows(
                WorkspaceMemberNotFoundException.class,
                () -> workspaceMemberService.changeRole(
                        11L,
                        99L,
                        7L,
                        WorkspaceRole.ADMIN
                )
        );
    }

    @Test
    void changeRoleRejectsOwnerTargetAndOwnerAssignment() {
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(21L, 11L))
                .thenReturn(Optional.of(ownerMembership));

        assertAll(
                () -> assertThrows(
                        WorkspaceOwnerMutationException.class,
                        () -> workspaceMemberService.changeRole(
                                11L,
                                21L,
                                7L,
                                WorkspaceRole.MEMBER
                        )
                ),
                () -> assertThrows(
                        WorkspaceOwnerMutationException.class,
                        () -> workspaceMemberService.changeRole(
                                11L,
                                22L,
                                7L,
                                WorkspaceRole.OWNER
                        )
                )
        );
    }

    @Test
    void removeDeletesScopedNonOwnerMembership() {
        User member = user(8L, "member@test.com", "Member");
        WorkspaceMember membership = membership(
                22L,
                WorkspaceMember.member(workspace, member)
        );
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(22L, 11L))
                .thenReturn(Optional.of(membership));

        workspaceMemberService.remove(11L, 22L, 7L);

        verify(workspaceMemberRepository).delete(membership);
        verify(accessChangePublisher).publish(11L);
    }

    @Test
    void removeRejectsOwnerAndDoesNotDelete() {
        when(workspaceAccessService.requireManager(11L, 7L))
                .thenReturn(ownerMembership);
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(21L, 11L))
                .thenReturn(Optional.of(ownerMembership));

        assertThrows(
                WorkspaceOwnerMutationException.class,
                () -> workspaceMemberService.remove(11L, 21L, 7L)
        );
        verify(workspaceMemberRepository, never()).delete(any());
        verify(accessChangePublisher, never()).publish(any());
    }

    private User user(Long id, String email, String displayName) {
        User user = new User(email, displayName);
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

    private WorkspaceMember membership(Long id, WorkspaceMember membership) {
        ReflectionTestUtils.setField(membership, "id", id);
        return membership;
    }
}
