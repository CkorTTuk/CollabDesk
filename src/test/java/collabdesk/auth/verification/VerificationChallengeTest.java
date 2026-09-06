package collabdesk.auth.verification;

import collabdesk.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationChallengeTest {
    private static final Instant ISSUED_AT =
            Instant.parse("2026-09-06T10:00:00Z");

    @Test
    void rotateStartsFreshTenMinuteChallenge() {
        VerificationChallenge challenge = challenge();
        challenge.registerFailedAttempt();
        challenge.consume(ISSUED_AT.plusSeconds(30));
        Instant resendAt = ISSUED_AT.plusSeconds(70);

        challenge.rotate(
                "member@example.com",
                "b".repeat(64),
                resendAt,
                resendAt.plusSeconds(600)
        );

        assertAll(
                () -> assertEquals(0, challenge.getFailedAttempts()),
                () -> assertNull(challenge.getConsumedAt()),
                () -> assertEquals(resendAt, challenge.getIssuedAt()),
                () -> assertEquals(
                        resendAt.plusSeconds(600),
                        challenge.getExpiresAt()
                ),
                () -> assertEquals("b".repeat(64), challenge.getCodeHash())
        );
    }

    @Test
    void tracksThreeFailedAttemptsAndExpiration() {
        VerificationChallenge challenge = challenge();

        challenge.registerFailedAttempt();
        challenge.registerFailedAttempt();
        assertTrue(challenge.hasAttemptsRemaining(3));
        challenge.registerFailedAttempt();

        assertAll(
                () -> assertFalse(challenge.hasAttemptsRemaining(3)),
                () -> assertFalse(challenge.isExpired(
                        ISSUED_AT.plusSeconds(599)
                )),
                () -> assertTrue(challenge.isExpired(
                        ISSUED_AT.plusSeconds(600)
                ))
        );
    }

    @Test
    void consumedChallengeCannotBeConsumedAgain() {
        VerificationChallenge challenge = challenge();
        challenge.consume(ISSUED_AT.plusSeconds(1));

        assertThrows(
                IllegalStateException.class,
                () -> challenge.consume(ISSUED_AT.plusSeconds(2))
        );
    }

    private VerificationChallenge challenge() {
        return VerificationChallenge.issue(
                new User("member@example.com", "Member"),
                VerificationPurpose.EMAIL_VERIFICATION,
                VerificationChannel.EMAIL,
                "member@example.com",
                "a".repeat(64),
                ISSUED_AT,
                ISSUED_AT.plusSeconds(600)
        );
    }
}
