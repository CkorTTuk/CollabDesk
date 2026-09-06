package collabdesk.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void keepsExistingRegistrationBehaviorCompletedAndVerified() {
        User user = new User("  MEMBER@Example.COM ", "  Existing User  ");

        assertAll(
                () -> assertEquals("member@example.com", user.getEmail()),
                () -> assertEquals("Existing User", user.getDisplayName()),
                () -> assertEquals("Existing User", user.getFirstName()),
                () -> assertTrue(user.isEmailVerified()),
                () -> assertTrue(user.isOnboardingCompleted())
        );
    }

    @Test
    void createsProviderVerifiedUserWithPendingOnboarding() {
        User user = User.pendingExternal(
                "oauth@example.com",
                "temporary-name"
        );

        assertAll(
                () -> assertTrue(user.isEmailVerified()),
                () -> assertFalse(user.isOnboardingCompleted()),
                () -> assertNull(user.getFirstName()),
                () -> assertNull(user.getLastName()),
                () -> assertEquals("temporary-name", user.getDisplayName())
        );
    }

    @Test
    void completesOnboardingWithNormalizedProfile() {
        User user = User.pendingExternal("oauth@example.com", "temporary-name");
        LocalDate birthDate = LocalDate.of(2000, 2, 3);

        user.completeOnboarding("  Alex  ", "  Morgan  ", birthDate);

        assertAll(
                () -> assertTrue(user.isOnboardingCompleted()),
                () -> assertEquals("Alex", user.getFirstName()),
                () -> assertEquals("Morgan", user.getLastName()),
                () -> assertEquals("Alex Morgan", user.getDisplayName()),
                () -> assertEquals(birthDate, user.getBirthDate()),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> user.completeOnboarding("Again", null, null)
                )
        );
    }

    @Test
    void treatsBlankOptionalProfileValuesAsMissing() {
        User user = User.pendingExternal("oauth@example.com", "temporary-name");

        user.completeOnboarding("Alex", "   ", null);
        user.changeAvatar("   ");

        assertAll(
                () -> assertNull(user.getLastName()),
                () -> assertNull(user.getAvatarKey()),
                () -> assertEquals("Alex", user.getDisplayName())
        );
    }

    @Test
    void rejectsInvalidProfileValues() {
        User user = User.pendingExternal("oauth@example.com", "temporary-name");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> user.completeOnboarding(" ", null, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> user.completeOnboarding(
                                "Alex",
                                null,
                                LocalDate.now().plusDays(1)
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> user.completeOnboarding(
                                "a".repeat(60),
                                "b".repeat(60),
                                null
                        )
                )
        );
    }

    @Test
    void verifiesPendingLocalEmailOnlyOnce() {
        User user = User.pendingLocal("local@example.com", "Local User");
        Instant verifiedAt = Instant.parse("2026-09-01T10:15:30Z");

        assertFalse(user.isEmailVerified());

        user.markEmailVerified(verifiedAt);
        user.markEmailVerified(verifiedAt.plusSeconds(60));

        assertAll(
                () -> assertTrue(user.isEmailVerified()),
                () -> assertEquals(verifiedAt, user.getEmailVerifiedAt()),
                () -> assertTrue(user.isOnboardingCompleted())
        );
    }

    @Test
    void createsUnverifiedLocalUserWithPendingOnboarding() {
        User user = User.pendingLocalOnboarding(
                "local@example.com",
                "local"
        );

        assertAll(
                () -> assertFalse(user.isEmailVerified()),
                () -> assertNull(user.getEmailVerifiedAt()),
                () -> assertFalse(user.isOnboardingCompleted()),
                () -> assertNull(user.getOnboardingCompletedAt()),
                () -> assertNull(user.getFirstName())
        );
    }
}
