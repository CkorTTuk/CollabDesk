package collabdesk.account.onboarding;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.auth.security.CollabDeskAuthorities;
import collabdesk.auth.security.GitHubOAuth2Principal;
import collabdesk.auth.security.GoogleOidcPrincipal;
import collabdesk.auth.verification.VerificationCodeGenerator;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OnboardingIntegrationTest {
    private static final String ONBOARDING_URL = "/api/v1/account/onboarding";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private VerificationCodeGenerator verificationCodeGenerator;

    @Test
    void localRegistrationSignsInToAnIncompleteProfileThenCompletesOnboarding()
            throws Exception {
        String email = "local-onboarding@example.com";
        when(verificationCodeGenerator.generate()).thenReturn("004271");
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "passwordConfirmation": "%s"
                                }
                                """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post(
                        "/api/v1/auth/email-verification/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "004271"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andReturn();
        HttpSession httpSession = loginResult.getRequest().getSession(false);
        assertTrue(httpSession instanceof MockHttpSession);
        MockHttpSession session = (MockHttpSession) httpSession;

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
        mockMvc.perform(get("/api/v1/workspaces").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Local",
                                  "lastName": "Member",
                                  "birthDate": "2000-05-20"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Local Member"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        mockMvc.perform(get("/api/v1/workspaces").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void completingOnboardingRefreshesTheSameSessionAndIsIdempotent()
            throws Exception {
        User currentUser = userRepository.saveAndFlush(User.pendingExternal(
                "new-user@example.com",
                "new-user"
        ));
        User otherUser = userRepository.saveAndFlush(User.pendingExternal(
                "other-user@example.com",
                "other-user"
        ));
        MockHttpSession session = sessionFor(currentUser);

        mockMvc.perform(get(ONBOARDING_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));

        mockMvc.perform(get("/api/v1/workspaces").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": %d,
                                  "firstName": "  Alex  ",
                                  "lastName": "   ",
                                  "birthDate": "2000-05-20"
                                }
                                """.formatted(otherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.firstName").value("Alex"))
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.birthDate").value("2000-05-20"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.suggestion").doesNotExist());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        mockMvc.perform(get("/api/v1/workspaces").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        var authentication = ((SecurityContextImpl) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        )).getAuthentication();
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority ->
                CollabDeskAuthorities.PROFILE_COMPLETE.equals(
                        authority.getAuthority()
                )
        ));

        User savedCurrentUser = userRepository.findById(currentUser.getId()).orElseThrow();
        User savedOtherUser = userRepository.findById(otherUser.getId()).orElseThrow();
        assertEquals("Alex", savedCurrentUser.getFirstName());
        assertNull(savedCurrentUser.getLastName());
        assertEquals(LocalDate.of(2000, 5, 20), savedCurrentUser.getBirthDate());
        assertTrue(savedCurrentUser.isOnboardingCompleted());
        assertFalse(savedOtherUser.isOnboardingCompleted());

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Different",
                                  "lastName": "Name",
                                  "birthDate": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"));
    }

    @Test
    void completionRequestValidationReturnsFieldErrors() throws Exception {
        User user = userRepository.saveAndFlush(User.pendingExternal(
                "validation@example.com",
                "validation"
        ));
        MockHttpSession session = sessionFor(user);

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "   ",
                                  "lastName": null,
                                  "birthDate": "2999-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.birthDate").exists());

        assertFalse(userRepository.findById(user.getId())
                .orElseThrow()
                .isOnboardingCompleted());
    }

    @Test
    void googleOnboardingPreservesOAuthAuthenticationAndClearsSuggestion()
            throws Exception {
        User user = userRepository.saveAndFlush(User.pendingExternal(
                "google-new@example.com",
                "google-new"
        ));
        MockHttpSession session = googleSessionFor(user);

        mockMvc.perform(get(ONBOARDING_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion.firstName").value("Suggested"))
                .andExpect(jsonPath("$.suggestion.lastName").value("Person"))
                .andExpect(jsonPath("$.suggestion.avatarUrl")
                        .value("https://example.com/avatar.png"));

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Suggested",
                                  "lastName": "Person",
                                  "birthDate": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.suggestion").doesNotExist());

        var authentication = ((SecurityContextImpl) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        )).getAuthentication();
        assertTrue(authentication instanceof OAuth2AuthenticationToken);
        assertTrue(authentication.getPrincipal() instanceof GoogleOidcPrincipal);
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority ->
                CollabDeskAuthorities.PROFILE_COMPLETE.equals(
                        authority.getAuthority()
                )
        ));
        assertNull(((GoogleOidcPrincipal) authentication.getPrincipal())
                .getExternalProfileSuggestion());
    }

    @Test
    void githubOnboardingRefreshesPrincipalInTheSameSession() throws Exception {
        User user = userRepository.saveAndFlush(User.pendingExternal(
                "github-new@example.com",
                "github-new"
        ));
        MockHttpSession session = githubSessionFor(user);

        mockMvc.perform(get(ONBOARDING_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion.firstName").value("GitHub"))
                .andExpect(jsonPath("$.suggestion.lastName").value("User"));

        mockMvc.perform(post(ONBOARDING_URL + "/complete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "GitHub",
                                  "lastName": "User",
                                  "birthDate": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        var authentication = ((SecurityContextImpl) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        )).getAuthentication();
        assertTrue(authentication instanceof OAuth2AuthenticationToken);
        assertTrue(authentication.getPrincipal() instanceof GitHubOAuth2Principal);
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority ->
                CollabDeskAuthorities.PROFILE_COMPLETE.equals(
                        authority.getAuthority()
                )
        ));
    }

    private static MockHttpSession sessionFor(User user) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isOnboardingCompleted(),
                null
        );
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication)
        );
        return session;
    }

    private static MockHttpSession googleSessionFor(User user) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "signed-id-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        "sub", "stable-google-subject",
                        "email", user.getEmail(),
                        "email_verified", true,
                        "given_name", " Suggested ",
                        "family_name", " Person ",
                        "picture", " https://example.com/avatar.png "
                )
        );
        var oidcUser = new DefaultOidcUser(List.of(), idToken, "sub");
        GoogleOidcPrincipal principal = new GoogleOidcPrincipal(user, oidcUser);
        var authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication)
        );
        return session;
    }

    private static MockHttpSession githubSessionFor(User user) {
        var oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of(
                        "id", 12345678L,
                        "login", "github-user",
                        "name", "GitHub User"
                ),
                "id"
        );
        GitHubOAuth2Principal principal = new GitHubOAuth2Principal(
                user,
                oauth2User,
                "GitHub",
                "User",
                null
        );
        var authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication)
        );
        return session;
    }
}
