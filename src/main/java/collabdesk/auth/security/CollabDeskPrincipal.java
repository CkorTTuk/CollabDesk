package collabdesk.auth.security;

import collabdesk.auth.external.ExternalProfileSuggestion;
import collabdesk.user.entity.UserStatus;
import org.jspecify.annotations.Nullable;

public interface CollabDeskPrincipal {
    Long getUserId();
    String getEmail();
    String getDisplayName();
    UserStatus getStatus();
    boolean isEmailVerified();
    boolean isOnboardingCompleted();

    @Nullable
    default ExternalProfileSuggestion getExternalProfileSuggestion() {
        return null;
    }
}
