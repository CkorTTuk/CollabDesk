package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static collabdesk.infrastructure.cache.CacheConfiguration
        .WORKSPACE_PROJECT_ACCESS_OVERVIEW;

/**
 * Cache boundary for workspace access overviews. Keeping caching here prevents
 * authorization and database-query code from depending directly on Redis.
 */
@Service
public class WorkspaceProjectAccessOverviewCacheService {

    private final WorkspaceProjectAccessOverviewQueryService queryService;

    public WorkspaceProjectAccessOverviewCacheService(
            WorkspaceProjectAccessOverviewQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @Cacheable(
            cacheNames = WORKSPACE_PROJECT_ACCESS_OVERVIEW,
            key = "#workspaceId + ':' + #currentUserId",
            unless = "#result == null"
    )
    /** Returns a cached overview or invokes the authoritative database query. */
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        return queryService.findForWorkspace(workspaceId, currentUserId);
    }
}
