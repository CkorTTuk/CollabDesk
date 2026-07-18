package collabdesk.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.Locale;

@Entity
@Table(name = "users")

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name= "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Nullable
    @Version
    private Long version;

    public User(String email, String display_name) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.displayName = display_name.trim();
        this.status = UserStatus.ACTIVE;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = this.createdAt;
    }
    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

}
