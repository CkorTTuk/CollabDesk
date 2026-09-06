package collabdesk.auth.security;

import collabdesk.auth.external.ExternalProfileSuggestion;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.ArrayList;
import java.util.Collection;


/** Combines Google's OIDC attributes with the linked CollabDesk account. */
public final class GoogleOidcPrincipal
        extends DefaultOidcUser
        implements CollabDeskPrincipal {

    private final Long userId;
    private final String email;
    private final String displayName;
    private final UserStatus status;
    private final boolean emailVerified;
    private final boolean onboardingCompleted;
    @Nullable
    private final ExternalProfileSuggestion externalProfileSuggestion;

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
        this.externalProfileSuggestion = onboardingCompleted
                ? null
                : new ExternalProfileSuggestion(
                        oidcUser.getGivenName(),
                        oidcUser.getFamilyName(),
                        oidcUser.getPicture()
                );
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

    @Override
    public @Nullable ExternalProfileSuggestion getExternalProfileSuggestion() {
        return externalProfileSuggestion;
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
