package collabdesk.workspace.service;

import collabdesk.TestcontainersConfiguration;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkspaceServiceRollbackTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void rollsBackWorkspaceWhenSavingMembershipFails() {
        User creator = userRepository.save(
                new User("rollback@test.com", "Rollback")
        );
        long workspaceCountBeforeCreate = workspaceRepository.count();

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenThrow(new IllegalStateException(
                        "Simulated membership persistence failure"
                ));

        assertThrowsExactly(
                IllegalStateException.class,
                () -> workspaceService.create(
                        creator.getId(),
                        "Rollback workspace",
                        "Description"
                )
        );

        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
        assertEquals(workspaceCountBeforeCreate, workspaceRepository.count());
    }
}
