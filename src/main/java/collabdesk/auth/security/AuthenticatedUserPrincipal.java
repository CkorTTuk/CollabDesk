package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/** Spring Security principal used for local password and verified-code sessions. */
public final class AuthenticatedUserPrincipal
        implements UserDetails, CredentialsContainer, CollabDeskPrincipal {
    @Getter
    private final Long userId;
    @Getter
    private final String email;
    @Getter
    private final String displayName;
    @Getter
    private final UserStatus status;
    private final boolean emailVerified;
    private final boolean onboardingCompleted;
    private String passwordHash;

    public AuthenticatedUserPrincipal(
            Long userId,
            String email,
            String displayName,
            UserStatus userStatus,
            boolean emailVerified,
            boolean onboardingCompleted,
            String passwordHash
    ) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.status = userStatus;
        this.emailVerified = emailVerified;
        this.onboardingCompleted = onboardingCompleted;
        this.passwordHash = passwordHash;
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
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return CollabDeskAuthorities.forProfile(onboardingCompleted);
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
