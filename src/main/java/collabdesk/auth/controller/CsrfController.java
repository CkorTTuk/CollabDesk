package collabdesk.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes a CSRF token that the browser must echo on state-changing requests. */
@RestController

@Tag(
        name = "Infrastructure",
        description = "Browser security support endpoints"
)
public class CsrfController {
    @GetMapping("/csrf")
    @Operation(
            summary = "Get a CSRF token",
            description = """
                    Returns the token and header name required by state-changing
                    requests such as POST, PATCH and DELETE.
                    """
    )
    @ApiResponse(responseCode = "200", description = "CSRF token issued")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }
}
