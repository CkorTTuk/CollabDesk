package collabdesk.auth.security;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LocalUserDetailsService implements UserDetailsService {
    private final AuthIdentityRepository authIdentityRepository;

    public LocalUserDetailsService(AuthIdentityRepository authIdentityRepository) {
        this.authIdentityRepository = authIdentityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        String email = username.trim().toLowerCase(Locale.ROOT);
        AuthIdentity authIdentity =
                authIdentityRepository.findByProviderAndProviderSubject(
                        AuthProvider.LOCAL,
                        email
                ).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        User user = authIdentity.getUser();
        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isOnboardingCompleted(),
                authIdentity.getPasswordHash()
                );
    }
}
