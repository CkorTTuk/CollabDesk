package collabdesk.auth.google;

import collabdesk.auth.security.GoogleOidcPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Extends Spring's Google OIDC loading with CollabDesk account reconciliation
 * and replaces the provider principal with the application's own principal.
 */
@Service
public class CollabDeskOidcUserService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;
    private final GoogleAccountService googleAccountService;

    @Autowired
    public CollabDeskOidcUserService(GoogleAccountService googleAccountService) {
        this(new OidcUserService(), googleAccountService);
    }

    CollabDeskOidcUserService(
            OidcUserService delegate,
            GoogleAccountService googleAccountService
    ) {
        this.delegate = delegate;
        this.googleAccountService = googleAccountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request)
            throws OAuth2AuthenticationException {
        String registrationId = request.getClientRegistration().getRegistrationId();
        if (!"google".equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_oidc_provider")
            );
        }

        OidcUser oidcUser = delegate.loadUser(request);
        var account = googleAccountService.findOrCreate(oidcUser);
        return new GoogleOidcPrincipal(account.user(), oidcUser);
    }
}
