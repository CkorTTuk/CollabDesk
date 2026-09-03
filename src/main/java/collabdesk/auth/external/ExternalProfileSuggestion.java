package collabdesk.auth.external;

import org.jspecify.annotations.Nullable;

public record ExternalProfileSuggestion(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String avatarUrl
) {
    public ExternalProfileSuggestion {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
        avatarUrl = normalize(avatarUrl);
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
