package collabdesk.account;

import collabdesk.auth.security.CollabDeskPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Current user's profile and preferences")
@SecurityRequirement(name = "sessionCookie")
public class AccountController {
    private final AccountQueryService accountQueryService;
    private final AccountProfileService accountProfileService;

    public AccountController(
            AccountQueryService accountQueryService,
            AccountProfileService accountProfileService
    ) {
        this.accountQueryService = accountQueryService;
        this.accountProfileService = accountProfileService;
    }

    @GetMapping
    @Operation(summary = "Get the current account")
    public AccountResponse getAccount(
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return accountQueryService.getAccount(principal.getUserId());
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update the current profile")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public AccountResponse updateProfile(
            @AuthenticationPrincipal CollabDeskPrincipal principal,
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return accountProfileService.updateProfile(
                principal.getUserId(), request, authentication,
                servletRequest, servletResponse
        );
    }

    @PatchMapping("/locale")
    @Operation(summary = "Update the current account language")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public AccountResponse updateLocale(
            @AuthenticationPrincipal CollabDeskPrincipal principal,
            @Valid @RequestBody UpdateLocaleRequest request
    ) {
        return accountProfileService.updateLocale(principal.getUserId(), request);
    }
}
