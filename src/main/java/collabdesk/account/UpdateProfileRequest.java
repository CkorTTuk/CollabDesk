package collabdesk.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Nullable @Size(max = 100) String lastName,
        @Nullable @PastOrPresent LocalDate birthDate
) {
    public UpdateProfileRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null || lastName.isBlank()
                ? null
                : lastName.strip();
    }
}
