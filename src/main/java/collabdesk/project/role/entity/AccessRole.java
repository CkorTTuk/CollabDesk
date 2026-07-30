package collabdesk.project.role.entity;

import collabdesk.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "access_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "access_roles_workspace_name_uk",
                columnNames = {"workspace_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessRole {

    private static final Pattern HEX_COLOR =
            Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Column(name = "color", length = 7, nullable = false)
    private String color;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AccessRole(Workspace workspace, String name, String color) {
        this.workspace = Objects.requireNonNull(
                workspace,
                "Workspace cannot be null"
        );
        this.name = normalizeName(name);
        this.color = normalizeColor(color);
    }

    public void update(String name, String color) {
        this.name = normalizeName(name);
        this.color = normalizeColor(color);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role name cannot be null");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 60) {
            throw new IllegalArgumentException(
                    "Role name must be between 2 and 60 characters"
            );
        }
        return normalized;
    }

    private static String normalizeColor(String value) {
        if (value == null || !HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Role color must use #RRGGBB format"
            );
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
