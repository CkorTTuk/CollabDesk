package collabdesk.account.avatar;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class LocalAvatarStorage implements AvatarStorage {
    private static final Pattern SAFE_KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)"
    );
    private final AvatarProperties properties;
    private Path root;

    public LocalAvatarStorage(AvatarProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        try {
            Path configured = Path.of(properties.getLocalDirectory())
                    .toAbsolutePath().normalize();
            Files.createDirectories(configured);
            root = configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new IllegalStateException("Avatar storage cannot be initialized", exception);
        }
    }

    @Override
    public StoredAvatar store(ValidatedAvatar avatar) {
        String key = UUID.randomUUID() + "." + avatar.extension();
        Path destination = resolveSafe(key);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            Files.write(temporary, avatar.bytes());
            try {
                Files.move(
                        temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            return new StoredAvatar(key);
        } catch (IOException exception) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            throw new IllegalStateException("Avatar could not be stored", exception);
        }
    }

    @Override
    public AvatarContent load(String key) {
        Path file = resolveSafe(key);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new AvatarNotFoundException();
            }
            return new AvatarContent(
                    Files.readAllBytes(file),
                    key.endsWith(".png") ? "image/png" : "image/jpeg"
            );
        } catch (AvatarNotFoundException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AvatarNotFoundException();
        }
    }

    @Override
    public void delete(String key) {
        Path file = resolveSafe(key);
        try {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Avatar key does not identify a regular file");
            }
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Avatar could not be deleted", exception);
        }
    }

    private Path resolveSafe(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid avatar key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid avatar key");
        }
        return resolved;
    }
}
