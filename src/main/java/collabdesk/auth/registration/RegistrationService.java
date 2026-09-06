package collabdesk.auth.registration;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.auth.verification.VerificationChallengeService;
import collabdesk.auth.verification.VerificationIssueResult;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;


/**
 * Registers a local account as one transaction: unverified user, BCrypt-backed
 * LOCAL identity, and the first email-verification challenge.
 */
@Service

public class RegistrationService {
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationChallengeService verificationChallengeService;

    public RegistrationService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            VerificationChallengeService verificationChallengeService
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationChallengeService = verificationChallengeService;
    }
    /** Creates every database record required before the verification email is sent. */
    @Transactional
    public RegistrationResult register(String email, String rawPassword){
        if( email == null || rawPassword == null){
            throw new NullPointerException("Parameters cannot be null");
        }
        if( email.isBlank() || rawPassword.isBlank() ){
            throw new IllegalArgumentException("You must provide all parameters non-Empty");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if( userRepository.existsByEmail(normalizedEmail) ){
            throw new EmailAlreadyExistsException("there is already an account with that email");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = User.pendingLocalOnboarding(
                normalizedEmail,
                temporaryDisplayName(normalizedEmail)
        );

        User savedUser = userRepository.save(user);

        AuthIdentity authIdentity = AuthIdentity.local( savedUser, normalizedEmail, passwordHash);
        authIdentityRepository.save(authIdentity);

        VerificationIssueResult verification =
                verificationChallengeService.issueEmailVerification(savedUser);

        return new RegistrationResult(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getDisplayName(),
                savedUser.getStatus(),
                true,
                verification.expiresAt(),
                verification.resendAvailableAt()
        );

    }

    private String temporaryDisplayName(String normalizedEmail) {
        int separatorIndex = normalizedEmail.indexOf('@');
        String localPart = separatorIndex > 0
                ? normalizedEmail.substring(0, separatorIndex)
                : "New member";
        return localPart.substring(0, Math.min(localPart.length(), 100));
    }
}
