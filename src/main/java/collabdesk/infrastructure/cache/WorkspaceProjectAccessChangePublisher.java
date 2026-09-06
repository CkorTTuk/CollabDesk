package collabdesk.infrastructure.cache;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes a domain event when access rules change so cache cleanup can wait
 * for the surrounding database transaction to commit.
 */
@Component
public class WorkspaceProjectAccessChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public WorkspaceProjectAccessChangePublisher(
            ApplicationEventPublisher eventPublisher
    ) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(Long workspaceId) {
        eventPublisher.publishEvent(
                new WorkspaceProjectAccessChangedEvent(workspaceId)
        );
    }
}
