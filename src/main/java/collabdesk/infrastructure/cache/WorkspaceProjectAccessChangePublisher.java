package collabdesk.infrastructure.cache;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
