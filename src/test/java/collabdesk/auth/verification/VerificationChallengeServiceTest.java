package collabdesk.auth.verification;

import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationChallengeServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-06T10:00:00Z");

    @Mock
    private VerificationChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationCodeGenerator codeGenerator;
    @Mock
    private VerificationCodeHasher codeHasher;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VerificationChallengeService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
    }

    @Test
    void issueCreatesChallengeForExactlyTenMinutesAndPublishesRawCode() {
        User user = pendingUser();
        when(challengeRepository.findByUser_IdAndPurpose(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION
        )).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("004271");
        when(codeHasher.hash(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "004271"
        )).thenReturn("a".repeat(64));

        VerificationIssueResult result = service.issueEmailVerification(user);

        ArgumentCaptor<VerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(VerificationChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture());
        VerificationChallenge challenge = challengeCaptor.getValue();
        assertEquals(NOW.plusSeconds(600), result.expiresAt());
        assertEquals(NOW.plusSeconds(60), result.resendAvailableAt());
        assertEquals(NOW.plusSeconds(600), challenge.getExpiresAt());
        assertEquals("a".repeat(64), challenge.getCodeHash());

        ArgumentCaptor<VerificationIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(VerificationIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("004271", eventCaptor.getValue().code());
    }

    @Test
    void resendRestartsTenMinutesFromNowInsteadOfExtendingOldExpiry() {
        User user = pendingUser();
        VerificationChallenge existing = VerificationChallenge.issue(
                user,
                VerificationPurpose.EMAIL_VERIFICATION,
                VerificationChannel.EMAIL,
                user.getEmail(),
                "a".repeat(64),
                NOW,
                NOW.plusSeconds(600)
        );
        Instant resendTime = NOW.plusSeconds(120);
        VerificationChallengeService laterService = serviceAt(resendTime);
        when(userRepository.findByEmailForUpdate(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(challengeRepository.findForUpdate(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION
        )).thenReturn(Optional.of(existing));
        when(codeGenerator.generate()).thenReturn("123456");
        when(codeHasher.hash(any(), any(), any(), any()))
                .thenReturn("b".repeat(64));

        laterService.resendEmailVerification(user.getEmail());

        assertEquals(resendTime, existing.getIssuedAt());
        assertEquals(resendTime.plusSeconds(600), existing.getExpiresAt());
        assertEquals("b".repeat(64), existing.getCodeHash());
    }

    @Test
    void resendBeforeSixtySecondsIsRejected() {
        User user = pendingUser();
        VerificationChallenge existing = VerificationChallenge.issue(
                user,
                VerificationPurpose.EMAIL_VERIFICATION,
                VerificationChannel.EMAIL,
                user.getEmail(),
                "a".repeat(64),
                NOW,
                NOW.plusSeconds(600)
        );
        VerificationChallengeService earlyService = serviceAt(
                NOW.plusSeconds(30)
        );
        when(userRepository.findByEmailForUpdate(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(challengeRepository.findForUpdate(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION
        )).thenReturn(Optional.of(existing));

        VerificationResendTooSoonException exception = assertThrows(
                VerificationResendTooSoonException.class,
                () -> earlyService.resendEmailVerification(user.getEmail())
        );

        assertEquals(30, exception.getRetryAfterSeconds());
    }

    @Test
    void thirdWrongAttemptExhaustsChallenge() {
        User user = pendingUser();
        VerificationChallenge challenge = challenge(user);
        challenge.registerFailedAttempt();
        challenge.registerFailedAttempt();
        when(userRepository.findByEmailForUpdate(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(challengeRepository.findForUpdate(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION
        )).thenReturn(Optional.of(challenge));
        when(codeHasher.matches(any(), any(), any(), any(), any()))
                .thenReturn(false);

        assertThrows(
                VerificationAttemptsExhaustedException.class,
                () -> service.confirmEmail(user.getEmail(), "000000")
        );

        assertEquals(3, challenge.getFailedAttempts());
        assertFalse(user.isEmailVerified());
    }

    @Test
    void correctCodeVerifiesUserAndConsumesChallenge() {
        User user = pendingUser();
        VerificationChallenge challenge = challenge(user);
        when(userRepository.findByEmailForUpdate(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(challengeRepository.findForUpdate(
                42L,
                VerificationPurpose.EMAIL_VERIFICATION
        )).thenReturn(Optional.of(challenge));
        when(codeHasher.matches(any(), any(), any(), any(), any()))
                .thenReturn(true);

        User confirmed = service.confirmEmail(user.getEmail(), "004271");

        assertEquals(user, confirmed);
        assertTrue(user.isEmailVerified());
        assertTrue(challenge.isConsumed());
    }

    private VerificationChallengeService serviceAt(Instant instant) {
        return new VerificationChallengeService(
                challengeRepository,
                userRepository,
                codeGenerator,
                codeHasher,
                eventPublisher,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private User pendingUser() {
        User user = User.pendingLocalOnboarding(
                "member@example.com",
                "member"
        );
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    private VerificationChallenge challenge(User user) {
        return VerificationChallenge.issue(
                user,
                VerificationPurpose.EMAIL_VERIFICATION,
                VerificationChannel.EMAIL,
                user.getEmail(),
                "a".repeat(64),
                NOW,
                NOW.plusSeconds(600)
        );
    }
}
