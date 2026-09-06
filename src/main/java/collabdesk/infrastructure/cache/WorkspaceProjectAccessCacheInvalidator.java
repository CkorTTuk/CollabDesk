package collabdesk.infrastructure.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static collabdesk.infrastructure.cache.CacheConfiguration.WORKSPACE_PROJECT_ACCESS_OVERVIEW;

/**
 * Evicts stale access projections after commit. A rolled-back transaction
 * therefore cannot remove a still-valid cache entry.
 */
@Component
@Profile("redis")
public class WorkspaceProjectAccessCacheInvalidator {

    @CacheEvict(
            cacheNames = WORKSPACE_PROJECT_ACCESS_OVERVIEW,
            allEntries = true
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(WorkspaceProjectAccessChangedEvent event) {
        // Cache eviction is applied by the Spring caching proxy after commit.
    }
}
