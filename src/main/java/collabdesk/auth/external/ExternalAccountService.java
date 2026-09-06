package collabdesk.auth.external;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Reconciles a verified external identity with local users and identities. It
 * either reuses a safe match or creates the pending profile atomically.
 */
@Service
public class ExternalAccountService {
    private static final Logger log = LoggerFactory.getLogger(
            ExternalAccountService.class
    );
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final AuthIdentityRepository authIdentityRepository;
    private final UserRepository userRepository;

    public ExternalAccountService(
            AuthIdentityRepository authIdentityRepository,
            UserRepository userRepository
    ) {
        this.authIdentityRepository = authIdentityRepository;
        this.userRepository = userRepository;
    }

    /** Resolves or creates the user represented by one trusted provider identity. */
    @Transactional
    public ExternalAccountResult findOrCreate(ExternalIdentity externalIdentity) {
        Objects.requireNonNull(externalIdentity, "externalIdentity is required");
        AuthProvider provider = requireExternalProvider(externalIdentity.provider());
        String providerSubject = requireValue(
                externalIdentity.providerSubject(),
                "external_subject_missing"
        );
        String email = normalizeVerifiedEmail(externalIdentity.verifiedEmail());

        return authIdentityRepository
                .findWithUserByProviderAndProviderSubject(provider, providerSubject)
                .map(AuthIdentity::getUser)
                .map(user -> result(requireActive(user), false))
                .orElseGet(() -> linkByEmailOrCreate(provider, providerSubject, email));
    }

    private ExternalAccountResult linkByEmailOrCreate(
            AuthProvider provider,
            String providerSubject,
            String email
    ) {
        User user = userRepository.findByEmail(email).orElse(null);
        boolean newlyCreated = user == null;

        if (newlyCreated) {
            user = userRepository.save(User.pendingExternal(
                    email,
                    temporaryDisplayName(email)
            ));
        } else {
            requireActive(user);
            if (authIdentityRepository.existsByUser_IdAndProvider(
                    user.getId(),
                    provider
            )) {
                log.warn(
                        "External identity conflict: provider={}, userId={}",
                        provider,
                        user.getId()
                );
                throw authenticationFailure("external_identity_conflict");
            }
        }

        authIdentityRepository.save(createIdentity(user, provider, providerSubject));
        return result(user, newlyCreated);
    }

    private AuthIdentity createIdentity(
            User user,
            AuthProvider provider,
            String providerSubject
    ) {
        return switch (provider) {
            case GOOGLE -> AuthIdentity.google(user, providerSubject);
            case GITHUB -> AuthIdentity.github(user, providerSubject);
            case LOCAL -> throw authenticationFailure("unsupported_external_provider");
        };
    }

    private AuthProvider requireExternalProvider(AuthProvider provider) {
        if (provider == null || provider == AuthProvider.LOCAL) {
            throw authenticationFailure("unsupported_external_provider");
        }
        return provider;
    }

    private String normalizeVerifiedEmail(String email) {
        if (email == null || email.isBlank()) {
            throw authenticationFailure("external_email_missing");
        }

        String normalized;
        try {
            normalized = User.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            throw authenticationFailure("external_email_invalid");
        }

        int separator = normalized.indexOf('@');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw authenticationFailure("external_email_invalid");
        }
        return normalized;
    }

    private String temporaryDisplayName(String email) {
        String localPart = email.substring(0, email.indexOf('@')).strip();
        return localPart.length() <= MAX_DISPLAY_NAME_LENGTH
                ? localPart
                : localPart.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }

    private String requireValue(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw authenticationFailure(errorCode);
        }
        return value.strip();
    }

    private User requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw authenticationFailure("account_disabled");
        }
        return user;
    }

    private ExternalAccountResult result(User user, boolean newlyCreated) {
        return new ExternalAccountResult(
                user,
                newlyCreated,
                !user.isOnboardingCompleted()
        );
    }

    private OAuth2AuthenticationException authenticationFailure(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }
}
