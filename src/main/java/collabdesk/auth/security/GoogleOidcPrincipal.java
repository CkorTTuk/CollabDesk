package collabdesk.auth.security;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;


public final class GoogleOidcPrincipal
        extends DefaultOidcUser
        implements CollabDeskPrincipal {

    private final Long userId;
    private final String email;
    private final String displayName;
    private final UserStatus status;

    public GoogleOidcPrincipal(User user, OidcUser oidcUser) {
        super(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.status = user.getStatus();
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public UserStatus getStatus() {
        return status;
    }
}
