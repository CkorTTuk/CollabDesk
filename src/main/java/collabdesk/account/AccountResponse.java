package collabdesk.account;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Current account profile and safe connected-provider summary")
public record AccountResponse(
        Long id,
        String email,
        String displayName,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable LocalDate birthDate,
        @Nullable String avatarUrl,
        boolean emailVerified,
        String preferredLocale,
        List<AuthProvider> providers
) {
    static AccountResponse from(
            User user,
            List<AuthProvider> providers,
            AvatarUrlFactory avatarUrlFactory
    ) {
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                avatarUrlFactory.create(user.getAvatarKey()),
                user.isEmailVerified(),
                user.getPreferredLocale(),
                List.copyOf(providers)
        );
    }
}
