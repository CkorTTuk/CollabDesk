package collabdesk.auth.external;

import collabdesk.auth.entity.AuthProvider;
import org.jspecify.annotations.Nullable;

public record ExternalIdentity(
        AuthProvider provider,
        String providerSubject,
        String verifiedEmail,
        @Nullable String suggestedFirstName,
        @Nullable String suggestedLastName,
        @Nullable String suggestedAvatarUrl
) {
}
