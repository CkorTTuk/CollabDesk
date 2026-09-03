package collabdesk.auth.github;

public record GitHubEmailResponse(
        String email,
        boolean primary,
        boolean verified
) {
}
