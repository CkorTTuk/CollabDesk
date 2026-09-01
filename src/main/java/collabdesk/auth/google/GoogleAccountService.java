package collabdesk.auth.google;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class GoogleAccountService {
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final AuthIdentityRepository authIdentityRepository;
    private final UserRepository userRepository;

    public GoogleAccountService(
            AuthIdentityRepository authIdentityRepository,
            UserRepository userRepository
    ) {
        this.authIdentityRepository = authIdentityRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public User findOrCreate(OidcUser oidcUser) {
        String subject = requireClaim(oidcUser.getSubject(), "google_subject_missing");
        String email = requireClaim(oidcUser.getEmail(), "google_email_missing")
                .toLowerCase(Locale.ROOT);

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw authenticationFailure("google_email_not_verified");
        }

        return authIdentityRepository
                .findWithUserByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        subject
                )
                .map(AuthIdentity::getUser)
                .map(this::requireActive)
                .orElseGet(() -> createOrLinkIdentity(oidcUser, subject, email));
    }

    private User createOrLinkIdentity(
            OidcUser oidcUser,
            String subject,
            String email
    ) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        new User(email, displayName(oidcUser, email))
                ));

        requireActive(user);

        if (authIdentityRepository.existsByUser_IdAndProvider(
                user.getId(),
                AuthProvider.GOOGLE
        )) {
            throw authenticationFailure("google_identity_conflict");
        }

        authIdentityRepository.save(AuthIdentity.google(user, subject));
        return user;
    }

    private User requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw authenticationFailure("account_disabled");
        }
        return user;
    }

    private String displayName(OidcUser oidcUser, String email) {
        String candidate = oidcUser.getFullName();
        if (candidate == null || candidate.isBlank()) {
            candidate = email.substring(0, email.indexOf('@'));
        }

        String normalized = candidate.strip();
        return normalized.length() <= MAX_DISPLAY_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_DISPLAY_NAME_LENGTH);
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
