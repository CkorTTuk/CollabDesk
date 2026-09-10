package collabdesk.account.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalAvatarStorageTest {
    @TempDir Path directory;

    @Test
    void storesLoadsDeletesAndNeverReusesKeys() {
        LocalAvatarStorage storage = storage();
        ValidatedAvatar avatar = new ValidatedAvatar(
                new byte[]{1, 2, 3}, "image/png", "png"
        );

        StoredAvatar first = storage.store(avatar);
        StoredAvatar second = storage.store(avatar);

        assertNotEquals(first.key(), second.key());
        assertArrayEquals(avatar.bytes(), storage.load(first.key()).bytes());
        storage.delete(first.key());
        assertThrows(AvatarNotFoundException.class, () -> storage.load(first.key()));
    }

    @Test
    void rejectsTraversalAbsoluteAndUnrecognizedKeys() {
        LocalAvatarStorage storage = storage();

        assertThrows(IllegalArgumentException.class, () -> storage.load("../secret.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.load("C:\\secret.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("avatar.png"));
    }

    private LocalAvatarStorage storage() {
        AvatarProperties properties = new AvatarProperties();
        properties.setLocalDirectory(directory.toString());
        LocalAvatarStorage storage = new LocalAvatarStorage(properties);
        storage.initialize();
        return storage;
    }
}
