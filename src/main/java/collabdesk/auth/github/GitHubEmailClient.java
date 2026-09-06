package collabdesk.auth.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;

/**
 * Small GitHub API client used only to select the primary verified email when
 * that address is absent from the standard OAuth profile response.
 */
@Component
public class GitHubEmailClient {
    private static final String EMAILS_PATH = "/user/emails";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public GitHubEmailClient() {
        this(createRestClient());
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(requestFactory)
                .build();
    }

    GitHubEmailClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Returns GitHub's primary verified email or fails the login safely. */
    public String findPrimaryVerifiedEmail(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw authenticationFailure("github_email_lookup_failed");
        }

        try {
            GitHubEmailResponse[] emails = restClient.get()
                    .uri(EMAILS_PATH)
                    .headers(headers -> addGitHubHeaders(headers, accessToken))
                    .retrieve()
                    .body(GitHubEmailResponse[].class);

            if (emails == null) {
                throw authenticationFailure("github_email_missing");
            }

            return Arrays.stream(emails)
                    .filter(email -> email.primary() && email.verified())
                    .map(GitHubEmailResponse::email)
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst()
                    .map(String::strip)
                    .orElseThrow(() -> authenticationFailure("github_email_missing"));
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // Do not attach the provider exception: it may contain request details.
            throw authenticationFailure("github_email_lookup_failed");
        }
    }

    private static void addGitHubHeaders(
            HttpHeaders headers,
            String accessToken
    ) {
        headers.setBearerAuth(accessToken);
        headers.setAccept(MediaType.parseMediaTypes("application/vnd.github+json"));
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
    }

    private static OAuth2AuthenticationException authenticationFailure(
            String errorCode
    ) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }
}
