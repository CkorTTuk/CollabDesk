package collabdesk.account.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record CompleteOnboardingRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Nullable
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @Nullable
        @PastOrPresent(message = "Birth date must not be in the future")
        LocalDate birthDate
) {
    public CompleteOnboardingRequest {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null ? null : value.strip();
    }
}
