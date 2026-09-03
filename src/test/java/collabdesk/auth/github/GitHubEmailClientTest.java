package collabdesk.auth.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubEmailClientTest {
    @Test
    void selectsOnlyPrimaryAndVerifiedEmail() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubEmailClient client = new GitHubEmailClient(builder.build());

        server.expect(once(), requestTo("https://api.github.test/user/emails"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess("""
                        [
                          {"email":"unverified@example.com","primary":true,"verified":false},
                          {"email":"secondary@example.com","primary":false,"verified":true},
                          {"email":"primary@example.com","primary":true,"verified":true}
                        ]
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertEquals(
                "primary@example.com",
                client.findPrimaryVerifiedEmail("test-access-token")
        );
        server.verify();
    }

    @Test
    void missingVerifiedEmailUsesSafeOAuthError() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubEmailClient client = new GitHubEmailClient(builder.build());
        server.expect(once(), requestTo("https://api.github.test/user/emails"))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> client.findPrimaryVerifiedEmail("secret-token")
        );

        assertEquals("github_email_missing", exception.getError().getErrorCode());
        assertFalse(exception.toString().contains("secret-token"));
    }

    @Test
    void providerErrorsDoNotExposeTokenOrResponseDetails() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubEmailClient client = new GitHubEmailClient(builder.build());
        server.expect(once(), requestTo("https://api.github.test/user/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("rate limit details"));

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> client.findPrimaryVerifiedEmail("secret-token")
        );

        assertEquals(
                "github_email_lookup_failed",
                exception.getError().getErrorCode()
        );
        assertFalse(exception.toString().contains("secret-token"));
        assertFalse(exception.toString().contains("rate limit details"));
    }
}
