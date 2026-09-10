package collabdesk.account;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** Converts an opaque storage key to an HTTP URL without exposing a filesystem path. */
@Component
public class AvatarUrlFactory {
    @Nullable
    public String create(@Nullable String avatarKey) {
        if (avatarKey == null || avatarKey.isBlank()) {
            return null;
        }
        return "/api/v1/avatars/" + UriUtils.encodePathSegment(
                avatarKey,
                StandardCharsets.UTF_8
        );
    }
}
