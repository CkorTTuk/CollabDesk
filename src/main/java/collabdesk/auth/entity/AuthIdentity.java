package collabdesk.auth.entity;

import collabdesk.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * A provider-specific way to authenticate one user. LOCAL identities hold a
 * password hash; external identities store only their provider subject.
 */
@Entity
@Table(name = "auth_identities")

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column( name = "provider", nullable = false , length = 20)
    private AuthProvider provider;

    @Column( name = "provider_subject", nullable = false, length = 320)
    @NotBlank
    private String providerSubject;

    @Column( name = "password_hash", length = 255)
    private String passwordHash;

    @Column( name = "created_at", nullable = false)
    private Timestamp createdAt;

    private AuthIdentity(
            User user,
            AuthProvider provider,
            String providerSubject,
            String passwordHash
    ) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.passwordHash = passwordHash;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    public static AuthIdentity local(
            User user,
            String providerSubject,
            String passwordHash
    ) {
        Objects.requireNonNull(user, "user must not be null");

        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "providerSubject must not be blank"
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "passwordHash must not be blank"
            );
        }

        return new AuthIdentity(
                user,
                AuthProvider.LOCAL,
                providerSubject,
                passwordHash
        );
    }
    public static AuthIdentity google(
            User user,
            String providerSubject
    ) {
        return external(user, AuthProvider.GOOGLE, providerSubject);
    }

    public static AuthIdentity github(
            User user,
            String providerSubject
    ) {
        return external(user, AuthProvider.GITHUB, providerSubject);
    }

    private static AuthIdentity external(
            User user,
            AuthProvider provider,
            String providerSubject
    ) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(provider, "provider must not be null");

        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "providerSubject must not be blank"
            );
        }


        return new AuthIdentity(
                user,
                provider,
                providerSubject,
                null
        );
    }
}
