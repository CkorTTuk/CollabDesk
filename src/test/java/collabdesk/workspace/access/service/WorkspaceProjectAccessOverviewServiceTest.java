package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectAccessOverviewServiceTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private WorkspaceProjectAccessOverviewCacheService cacheService;

    private WorkspaceProjectAccessOverviewService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceProjectAccessOverviewService(
                workspaceAccessService,
                cacheService
        );
    }

    @Test
    void checksCurrentMembershipBeforeReadingCache() {
        WorkspaceProjectAccessOverviewResponse cached =
                new WorkspaceProjectAccessOverviewResponse(List.of());
        when(cacheService.findForWorkspace(1L, 10L)).thenReturn(cached);

        WorkspaceProjectAccessOverviewResponse result =
                service.findForWorkspace(1L, 10L);

        InOrder order = inOrder(workspaceAccessService, cacheService);
        order.verify(workspaceAccessService).requireMember(1L, 10L);
        order.verify(cacheService).findForWorkspace(1L, 10L);
        assertSame(cached, result);
    }

    @Test
    void deniedMembershipNeverReachesCachedResponse() {
        when(workspaceAccessService.requireMember(1L, 10L))
                .thenThrow(new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> service.findForWorkspace(1L, 10L)
        );

        verify(cacheService, never()).findForWorkspace(1L, 10L);
    }
}
