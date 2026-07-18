package collabdesk.auth.entity;

import collabdesk.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

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
    private String providerSubject;

    @Column( name = "password_hash", length = 255)
    private String passwordHash;

    @Column( name = "created_at", nullable = false)
    private Timestamp createdAt;

    public AuthIdentity(
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
}
