package collabdesk.controller;

import collabdesk.auth.dto.CurrentUserResponse;
import collabdesk.auth.dto.RegisterRequest;
import collabdesk.auth.dto.RegisterResponse;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.auth.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest) {
        return RegisterResponse.from(
                registrationService.register(
                        registerRequest.email(), registerRequest.displayName(), registerRequest.password()
                )
        );
    }
    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return CurrentUserResponse.from(principal);
    }
}
