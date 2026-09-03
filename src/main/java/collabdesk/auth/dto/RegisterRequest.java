package collabdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Data required to create a local CollabDesk account")
public record RegisterRequest(
        @Schema(example = "member@example.com")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
        @Schema(example = "password123", minLength = 8, maxLength = 64)
        @NotBlank
        @Size(min = 8, max = 64)
        String password,
        @Schema(example = "password123", minLength = 8, maxLength = 64)
        @NotBlank
        @Size(min = 8, max = 64)
        String passwordConfirmation
) {
}
