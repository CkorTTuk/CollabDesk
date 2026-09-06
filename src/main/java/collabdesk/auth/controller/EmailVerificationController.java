package collabdesk.auth.controller;

import collabdesk.auth.dto.ConfirmEmailVerificationRequest;
import collabdesk.auth.dto.CurrentUserResponse;
import collabdesk.auth.dto.ResendEmailVerificationRequest;
import collabdesk.auth.dto.VerificationIssueResponse;
import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.auth.security.EmailVerificationSessionService;
import collabdesk.auth.verification.VerificationChallengeService;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for confirming and resending local email verification codes. */
@RestController
@RequestMapping("/api/v1/auth/email-verification")
@Tag(
        name = "Authentication",
        description = "Account registration and current HTTP session"
)
public class EmailVerificationController {
    private final VerificationChallengeService verificationChallengeService;
    private final EmailVerificationSessionService sessionService;

    public EmailVerificationController(
            VerificationChallengeService verificationChallengeService,
            EmailVerificationSessionService sessionService
    ) {
        this.verificationChallengeService = verificationChallengeService;
        this.sessionService = sessionService;
    }

    @PostMapping("/confirm")
    @Operation(
            summary = "Confirm a local email address",
            description = "Consumes the six-digit code and creates an authenticated HTTP session"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email confirmed and session created"),
            @ApiResponse(
                    responseCode = "422",
                    description = "Code is invalid or expired",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Maximum failed attempts reached",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            )
    })
    public CurrentUserResponse confirm(
            @Valid @RequestBody ConfirmEmailVerificationRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user = verificationChallengeService.confirmEmail(
                requestBody.email(),
                requestBody.code()
        );
        AuthenticatedUserPrincipal principal = sessionService.authenticate(
                user,
                request,
                response
        );
        return CurrentUserResponse.from(principal);
    }

    @PostMapping("/resend")
    @Operation(
            summary = "Resend a local email verification code",
            description = "Rotates the current code without revealing whether the account exists"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted"),
            @ApiResponse(
                    responseCode = "429",
                    description = "A code was issued less than 60 seconds ago",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            )
    })
    public VerificationIssueResponse resend(
            @Valid @RequestBody ResendEmailVerificationRequest requestBody
    ) {
        return VerificationIssueResponse.from(
                verificationChallengeService.resendEmailVerification(
                        requestBody.email()
                )
        );
    }
}
