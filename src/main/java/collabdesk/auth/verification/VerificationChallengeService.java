package collabdesk.auth.verification;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class VerificationChallengeService {
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final VerificationChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeHasher codeHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public VerificationChallengeService(
            VerificationChallengeRepository challengeRepository,
            UserRepository userRepository,
            VerificationCodeGenerator codeGenerator,
            VerificationCodeHasher codeHasher,
            ApplicationEventPublisher eventPublisher
    ) {
        this(
                challengeRepository,
                userRepository,
                codeGenerator,
                codeHasher,
                eventPublisher,
                Clock.systemUTC()
        );
    }

    VerificationChallengeService(
            VerificationChallengeRepository challengeRepository,
            UserRepository userRepository,
            VerificationCodeGenerator codeGenerator,
            VerificationCodeHasher codeHasher,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public VerificationIssueResult issueEmailVerification(User user) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("user must be persisted first");
        }
        if (user.getStatus() != UserStatus.ACTIVE || user.isEmailVerified()) {
            throw new IllegalStateException("user does not require email verification");
        }
        return issueOrRotate(user, false);
    }

    @Transactional
    public VerificationIssueResult resendEmailVerification(String email) {
        Instant now = clock.instant();
        String normalizedEmail = User.normalizeEmail(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElse(null);
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || user.isEmailVerified()) {
            return syntheticIssueResult(now);
        }
        return issueOrRotate(user, true);
    }

    @Transactional(noRollbackFor = {
            InvalidVerificationCodeException.class,
            VerificationAttemptsExhaustedException.class
    })
    public User confirmEmail(String email, String code) {
        String normalizedEmail = User.normalizeEmail(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(InvalidVerificationCodeException::new);
        VerificationChallenge challenge = challengeRepository.findForUpdate(
                user.getId(),
                VerificationPurpose.EMAIL_VERIFICATION
        ).orElseThrow(InvalidVerificationCodeException::new);

        Instant now = clock.instant();
        if (challenge.isConsumed() || challenge.isExpired(now)) {
            throw new InvalidVerificationCodeException();
        }
        if (!challenge.hasAttemptsRemaining(MAX_FAILED_ATTEMPTS)) {
            throw new VerificationAttemptsExhaustedException();
        }
        boolean matches = codeHasher.matches(
                challenge.getCodeHash(),
                user.getId(),
                challenge.getPurpose(),
                challenge.getDestination(),
                code
        );
        if (!matches) {
            challenge.registerFailedAttempt();
            challengeRepository.save(challenge);
            if (!challenge.hasAttemptsRemaining(MAX_FAILED_ATTEMPTS)) {
                throw new VerificationAttemptsExhaustedException();
            }
            throw new InvalidVerificationCodeException();
        }

        user.markEmailVerified(now);
        challenge.consume(now);
        return user;
    }

    private VerificationIssueResult issueOrRotate(
            User user,
            boolean enforceCooldown
    ) {
        Instant now = clock.instant();
        VerificationChallenge challenge = (enforceCooldown
                ? challengeRepository.findForUpdate(
                        user.getId(),
                        VerificationPurpose.EMAIL_VERIFICATION
                )
                : challengeRepository.findByUser_IdAndPurpose(
                        user.getId(),
                        VerificationPurpose.EMAIL_VERIFICATION
                )).orElse(null);
        if (enforceCooldown && challenge != null) {
            Instant resendAvailableAt = challenge.getIssuedAt()
                    .plus(RESEND_COOLDOWN);
            if (now.isBefore(resendAvailableAt)) {
                long seconds = Math.max(
                        1,
                        Duration.between(now, resendAvailableAt).toSeconds()
                );
                throw new VerificationResendTooSoonException(seconds);
            }
        }

        String code = codeGenerator.generate();
        String destination = user.getEmail();
        Instant expiresAt = now.plus(CODE_TTL);
        String codeHash = codeHasher.hash(
                user.getId(),
                VerificationPurpose.EMAIL_VERIFICATION,
                destination,
                code
        );
        if (challenge == null) {
            challenge = VerificationChallenge.issue(
                    user,
                    VerificationPurpose.EMAIL_VERIFICATION,
                    VerificationChannel.EMAIL,
                    destination,
                    codeHash,
                    now,
                    expiresAt
            );
        } else {
            challenge.rotate(destination, codeHash, now, expiresAt);
        }
        challengeRepository.save(challenge);
        eventPublisher.publishEvent(
                new VerificationIssuedEvent(destination, code, CODE_TTL)
        );
        return new VerificationIssueResult(
                expiresAt,
                now.plus(RESEND_COOLDOWN)
        );
    }

    private VerificationIssueResult syntheticIssueResult(Instant now) {
        return new VerificationIssueResult(
                now.plus(CODE_TTL),
                now.plus(RESEND_COOLDOWN)
        );
    }
}
