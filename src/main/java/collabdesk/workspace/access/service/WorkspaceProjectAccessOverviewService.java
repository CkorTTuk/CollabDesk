package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);
        return cacheService.findForWorkspace(workspaceId, currentUserId);
    }

}
