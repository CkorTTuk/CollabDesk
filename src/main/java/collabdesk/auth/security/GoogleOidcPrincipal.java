package collabdesk.auth.security;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.ArrayList;
import java.util.Collection;


public final class GoogleOidcPrincipal
        extends DefaultOidcUser
        implements CollabDeskPrincipal {

    private final Long userId;
    private final String email;
    private final String displayName;
    private final UserStatus status;
    private final boolean emailVerified;
    private final boolean onboardingCompleted;

    public GoogleOidcPrincipal(User user, OidcUser oidcUser) {
        super(
                authorities(user, oidcUser),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.status = user.getStatus();
        this.emailVerified = user.isEmailVerified();
        this.onboardingCompleted = user.isOnboardingCompleted();
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

    @Override
    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    private static Collection<GrantedAuthority> authorities(
            User user,
            OidcUser oidcUser
    ) {
        ArrayList<GrantedAuthority> authorities = new ArrayList<>(
                oidcUser.getAuthorities()
        );
        for (GrantedAuthority authority : CollabDeskAuthorities.forProfile(
                user.isOnboardingCompleted()
        )) {
            if (!authorities.contains(authority)) {
                authorities.add(authority);
            }
        }
        return authorities;
    }
}
