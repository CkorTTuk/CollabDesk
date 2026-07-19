package collabdesk.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 320, unique = true)
    @NotBlank
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    @NotBlank
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

    public User(String email, String displayName) {
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(email, "email is required");
        if(!checkParameters(email, displayName)){
            throw new IllegalArgumentException("invalid email or display name");
        }
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.displayName = displayName.trim();
        this.status = UserStatus.ACTIVE;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = this.createdAt;
    }
    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    private boolean checkParameters(String email, String displayName) {
        if( email.trim().isBlank() || displayName.trim().isBlank() ){
            return false;
        }
        else return true;
    }

}
