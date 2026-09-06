package collabdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Local account that needs another verification code")
public record ResendEmailVerificationRequest(
        @Schema(example = "member@example.com")
        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}
