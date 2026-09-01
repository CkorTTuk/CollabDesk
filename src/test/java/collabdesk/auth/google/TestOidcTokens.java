package collabdesk.auth.google;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.Map;

final class TestOidcTokens {
    private TestOidcTokens() {
    }

    static OidcIdToken idToken() {
        Instant now = Instant.now();
        return new OidcIdToken(
                "signed-id-token",
                now,
                now.plusSeconds(300),
                Map.of("sub", "stable-google-subject")
        );
    }
}
