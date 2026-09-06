package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for the workspace access overview. It verifies that the caller
 * belongs to the workspace before reading the cached projection.
 */
@Service
public class WorkspaceProjectAccessOverviewService {
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceProjectAccessOverviewCacheService cacheService;

    public WorkspaceProjectAccessOverviewService(
            WorkspaceAccessService workspaceAccessService,
            WorkspaceProjectAccessOverviewCacheService cacheService
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.cacheService = cacheService;
    }
    /** Authorizes the caller and returns the workspace's effective access map. */
    @Transactional(readOnly = true)
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);
        return cacheService.findForWorkspace(workspaceId, currentUserId);
    }

}
