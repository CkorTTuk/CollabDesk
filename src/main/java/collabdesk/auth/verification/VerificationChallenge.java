package collabdesk.auth.verification;

import collabdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "verification_challenges",
    uniqueConstraints = @UniqueConstraint(
            name = "uq_verification_challenge_user_purpose",
            columnNames = {"user_id", "purpose"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private VerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private VerificationChannel channel;

    @Column(name = "destination", nullable = false, length = 320)
    private String destination;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static VerificationChallenge issue(
            User user,
            VerificationPurpose purpose,
            VerificationChannel channel,
            String destination,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        VerificationChallenge challenge = new VerificationChallenge();
        challenge.user = Objects.requireNonNull(user, "user is required");
        challenge.purpose = Objects.requireNonNull(purpose, "purpose is required");
        challenge.channel = Objects.requireNonNull(channel, "channel is required");
        challenge.rotate(destination, codeHash, issuedAt, expiresAt);
        return challenge;
    }

    public void rotate(
            String destination,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        String normalizedDestination = Objects.requireNonNull(
                destination,
                "destination is required"
        ).trim();
        String normalizedHash = Objects.requireNonNull(
                codeHash,
                "codeHash is required"
        ).trim();
        Instant issued = Objects.requireNonNull(issuedAt, "issuedAt is required");
        Instant expires = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (normalizedDestination.isEmpty()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        if (normalizedDestination.length() > 320) {
            throw new IllegalArgumentException("destination is too long");
        }
        if (normalizedHash.isEmpty() || normalizedHash.length() > 64) {
            throw new IllegalArgumentException("codeHash is invalid");
        }
        if (!expires.isAfter(issued)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.destination = normalizedDestination;
        this.codeHash = normalizedHash;
        this.issuedAt = issued;
        this.expiresAt = expires;
        this.consumedAt = null;
        this.failedAttempts = 0;
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now is required").isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean hasAttemptsRemaining(int maximumAttempts) {
        return failedAttempts < maximumAttempts;
    }

    public void registerFailedAttempt() {
        failedAttempts++;
    }

    public void consume(Instant now) {
        Instant consumed = Objects.requireNonNull(now, "now is required");
        if (isConsumed()) {
            throw new IllegalStateException("challenge is already consumed");
        }
        if (isExpired(consumed)) {
            throw new IllegalStateException("challenge is expired");
        }
        this.consumedAt = consumed;
    }
}
