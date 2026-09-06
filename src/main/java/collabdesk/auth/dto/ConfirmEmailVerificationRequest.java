package collabdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and six-digit code used to confirm a local account")
public record ConfirmEmailVerificationRequest(
        @Schema(example = "member@example.com")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
        @Schema(example = "004271", pattern = "^[0-9]{6}$")
        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$")
        String code
) {
}
