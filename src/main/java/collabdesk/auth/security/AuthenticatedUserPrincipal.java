package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

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
    private String passwordHash;

    public AuthenticatedUserPrincipal(Long userId, String email, String displayName, UserStatus userStatus, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.status = userStatus;
        this.passwordHash = passwordHash;
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
        return List.of();
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
