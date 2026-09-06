package collabdesk.auth.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationCodeHasherTest {
    private final VerificationCodeHasher hasher =
            new VerificationCodeHasher("test-pepper-not-for-production");

    @Test
    void hashIsBoundToUserPurposeDestinationAndCode() {
        String hash = hasher.hash(
                10L,
                VerificationPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "004271"
        );

        assertTrue(hasher.matches(
                hash,
                10L,
                VerificationPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "004271"
        ));
        assertFalse(hasher.matches(
                hash,
                10L,
                VerificationPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "004272"
        ));
        assertNotEquals("004271", hash);
        assertNotEquals(hash, hasher.hash(
                11L,
                VerificationPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "004271"
        ));
        assertNotEquals(hash, hasher.hash(
                10L,
                VerificationPurpose.PASSWORD_RESET,
                "member@example.com",
                "004271"
        ));
    }
}
