package collabdesk.account.avatar;

import collabdesk.account.AccountResponse;
import collabdesk.auth.security.CollabDeskPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account avatar", description = "Secure current-user avatar storage")
@SecurityRequirement(name = "sessionCookie")
public class AvatarController {
    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping(value = "/account/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the current avatar")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public AccountResponse upload(
            @AuthenticationPrincipal CollabDeskPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        return avatarService.replace(principal.getUserId(), file);
    }

    @DeleteMapping("/account/avatar")
    @Operation(summary = "Remove the current avatar")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public AccountResponse remove(
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return avatarService.remove(principal.getUserId());
    }

    @GetMapping("/avatars/{avatarKey}")
    @Operation(summary = "Read an immutable avatar")
    public ResponseEntity<byte[]> read(@PathVariable String avatarKey) {
        AvatarContent avatar = avatarService.load(avatarKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.bytes());
    }
}
