package collabdesk.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_KEY_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 320, unique = true)
    @NotBlank
    private String email;

    @Column(name = "display_name", nullable = false, length = MAX_NAME_LENGTH)
    @NotBlank
    private String displayName;

    @Nullable
    @Column(name = "first_name", length = MAX_NAME_LENGTH)
    private String firstName;

    @Nullable
    @Column(name = "last_name", length = MAX_NAME_LENGTH)
    private String lastName;

    @Nullable
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Nullable
    @Column(name = "avatar_key", length = MAX_AVATAR_KEY_LENGTH)
    private String avatarKey;

    @Nullable
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Nullable
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Nullable
    @Version
    private Long version;

    public User(String email, String displayName) {
        this.email = normalizeEmail(email);
        this.displayName = normalizeRequired(
                displayName,
                "displayName",
                MAX_NAME_LENGTH
        );
        this.firstName = this.displayName;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = this.createdAt;
        this.emailVerifiedAt = this.createdAt.toInstant();
        this.onboardingCompletedAt = this.createdAt.toInstant();
    }

    public static User pendingExternal(String email, String temporaryDisplayName) {
        User user = new User(email, temporaryDisplayName);
        user.firstName = null;
        user.lastName = null;
        user.birthDate = null;
        user.avatarKey = null;
        user.onboardingCompletedAt = null;
        return user;
    }

    public static User pendingLocal(String email, String displayName) {
        User user = new User(email, displayName);
        user.emailVerifiedAt = null;
        return user;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public void completeOnboarding(
            String firstName,
            @Nullable String lastName,
            @Nullable LocalDate birthDate
    ) {
        if (isOnboardingCompleted()) {
            throw new IllegalStateException("onboarding is already completed");
        }
        applyProfile(firstName, lastName, birthDate);
        this.onboardingCompletedAt = Instant.now();
    }

    public void changeProfile(
            String firstName,
            @Nullable String lastName,
            @Nullable LocalDate birthDate
    ) {
        if (!isOnboardingCompleted()) {
            throw new IllegalStateException("onboarding must be completed first");
        }
        applyProfile(firstName, lastName, birthDate);
    }

    public void changeAvatar(@Nullable String avatarKey) {
        String normalizedAvatarKey = normalizeOptional(
                avatarKey,
                "avatarKey",
                MAX_AVATAR_KEY_LENGTH
        );
        if (Objects.equals(this.avatarKey, normalizedAvatarKey)) {
            return;
        }
        this.avatarKey = normalizedAvatarKey;
        touch();
    }

    public void markEmailVerified(Instant verifiedAt) {
        Objects.requireNonNull(verifiedAt, "verifiedAt is required");
        if (emailVerifiedAt != null) {
            return;
        }
        this.emailVerifiedAt = verifiedAt;
        touch();
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
        touch();
    }

    private void applyProfile(
            String firstName,
            @Nullable String lastName,
            @Nullable LocalDate birthDate
    ) {
        String normalizedFirstName = normalizeRequired(
                firstName,
                "firstName",
                MAX_NAME_LENGTH
        );
        String normalizedLastName = normalizeOptional(
                lastName,
                "lastName",
                MAX_NAME_LENGTH
        );
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate must not be in the future");
        }

        String newDisplayName = normalizedLastName == null
                ? normalizedFirstName
                : normalizedFirstName + " " + normalizedLastName;
        if (newDisplayName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("combined display name is too long");
        }

        this.firstName = normalizedFirstName;
        this.lastName = normalizedLastName;
        this.birthDate = birthDate;
        this.displayName = newDisplayName;
        touch();
    }

    public static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email is required");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (normalized.length() > 320) {
            throw new IllegalArgumentException("email is too long");
        }
        return normalized;
    }

    public static String normalizeRequired(
            String value,
            String fieldName,
            int maxLength
    ) {
        Objects.requireNonNull(value, fieldName + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }

    @Nullable
    private static String normalizeOptional(
            @Nullable String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }

    private void touch() {
        this.updatedAt = Timestamp.from(Instant.now());
    }
}
