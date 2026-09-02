package collabdesk.auth.google;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.external.ExternalAccountResult;
import collabdesk.auth.external.ExternalAccountService;
import collabdesk.auth.external.ExternalIdentity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleAccountService {
    private final ExternalAccountService externalAccountService;

    public GoogleAccountService(ExternalAccountService externalAccountService) {
        this.externalAccountService = externalAccountService;
    }

    public ExternalAccountResult findOrCreate(OidcUser oidcUser) {
        String subject = requireClaim(oidcUser.getSubject(), "google_subject_missing");
        String email = requireClaim(oidcUser.getEmail(), "google_email_missing");

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw authenticationFailure("google_email_not_verified");
        }

        return externalAccountService.findOrCreate(new ExternalIdentity(
                AuthProvider.GOOGLE,
                subject,
                email,
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                oidcUser.getPicture()
        ));
    }

    private String requireClaim(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw authenticationFailure(errorCode);
        }
        return value.strip();
    }

    private OAuth2AuthenticationException authenticationFailure(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }
}
