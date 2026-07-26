package collabdesk.controller;

import collabdesk.auth.dto.CurrentUserResponse;
import collabdesk.auth.dto.RegisterRequest;
import collabdesk.auth.dto.RegisterResponse;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")

@Tag(
        name = "Authentication",
        description = "Account registration and current HTTP session"
)
public class AuthController {
    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register an account",
            description = "Creates a new active local CollabDesk account"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email is already registered",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest) {
        return RegisterResponse.from(
                registrationService.register(
                        registerRequest.email(), registerRequest.displayName(), registerRequest.password()
                )
        );
    }
    @GetMapping("/me")
    @Operation(
            summary = "Get current account",
            description = "Returns the account represented by the HTTP session"
    )
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current account"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return CurrentUserResponse.from(principal);
    }
}
