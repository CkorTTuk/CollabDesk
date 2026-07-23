package collabdesk.authentication;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.auth.security.AuthenticatedUserPrincipal;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void successfulLoginPersistsAuthenticationAndExposesSafeCurrentUser() throws Exception {
        registrationService.register(
                "  Student@Example.com  ",
                "Student",
                PASSWORD
        );

        MvcResult loginResult = login(
                " STUDENT@example.com ",
                PASSWORD
        ).andExpect(status().isNoContent())
         .andReturn();

        MockHttpSession session = sessionFrom(loginResult);
        SecurityContext securityContext = securityContextFrom(session);
        Authentication authentication = securityContext.getAuthentication();

        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal);
        assertNull(((AuthenticatedUserPrincipal) authentication.getPrincipal()).getPassword());

        mockMvc.perform(get(ME_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.displayName").value("Student"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist());
    }

    @Test
    void badPasswordReturnsUnauthorizedWithoutAuthentication() throws Exception {
        registrationService.register(
                "bad-password@example.com",
                "Bad Password",
                PASSWORD
        );

        MvcResult result = login(
                "bad-password@example.com",
                "wrong-password"
        ).andExpect(status().isUnauthorized())
         .andReturn();

        assertNotAuthenticated(result);
    }

    @Test
    void unknownEmailReturnsSameUnauthorizedResponse() throws Exception {
        MvcResult result = login(
                "missing@example.com",
                PASSWORD
        ).andExpect(status().isUnauthorized())
         .andReturn();

        assertNotAuthenticated(result);
    }

    @Test
    void disabledAccountCannotLoginWithCorrectPassword() throws Exception {
        registrationService.register(
                "disabled@example.com",
                "Disabled User",
                PASSWORD
        );
        User user = userRepository.findByEmail("disabled@example.com")
                .orElseThrow();
        user.disable();
        userRepository.saveAndFlush(user);

        MvcResult result = login(
                "disabled@example.com",
                PASSWORD
        ).andExpect(status().isUnauthorized())
         .andReturn();

        assertNotAuthenticated(result);
    }

    @Test
    void loginWithoutCsrfIsForbiddenAndDoesNotAuthenticate() throws Exception {
        registrationService.register(
                "no-csrf@example.com",
                "No CSRF",
                PASSWORD
        );

        MvcResult result = mockMvc.perform(
                post(LOGIN_URL)
                        .param("email", "no-csrf@example.com")
                        .param("password", PASSWORD)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        ).andExpect(status().isForbidden())
         .andReturn();

        assertNotAuthenticated(result);
    }

    @Test
    void logoutInvalidatesAuthenticatedSession() throws Exception {
        registrationService.register(
                "logout@example.com",
                "Logout User",
                PASSWORD
        );
        MockHttpSession session = sessionFrom(
                login("logout@example.com", PASSWORD)
                        .andExpect(status().isNoContent())
                        .andReturn()
        );

        mockMvc.perform(
                post(LOGOUT_URL)
                        .session(session)
                        .with(csrf())
        ).andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }

    @Test
    void logoutWithoutCsrfIsForbiddenAndKeepsAuthentication() throws Exception {
        registrationService.register(
                "logout-no-csrf@example.com",
                "Logout Without CSRF",
                PASSWORD
        );
        MockHttpSession session = sessionFrom(
                login("logout-no-csrf@example.com", PASSWORD)
                        .andExpect(status().isNoContent())
                        .andReturn()
        );

        mockMvc.perform(
                post(LOGOUT_URL).session(session)
        ).andExpect(status().isForbidden());

        assertFalse(session.isInvalid());
        mockMvc.perform(get(ME_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("logout-no-csrf@example.com"));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(
                post(LOGIN_URL)
                        .with(csrf())
                        .param("email", email)
                        .param("password", password)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        );
    }

    private MockHttpSession sessionFrom(MvcResult result) {
        HttpSession session = result.getRequest().getSession(false);
        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private SecurityContext securityContextFrom(MockHttpSession session) {
        Object value = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertTrue(value instanceof SecurityContext);
        return (SecurityContext) value;
    }

    private void assertNotAuthenticated(MvcResult result) throws Exception {
        HttpSession session = result.getRequest().getSession(false);
        if (session != null) {
            Object context = session.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
            );
            assertTrue(
                    context == null
                            || !(context instanceof SecurityContext securityContext)
                            || securityContext.getAuthentication() == null
                            || !securityContext.getAuthentication().isAuthenticated()
            );
            mockMvc.perform(get(ME_URL).session((MockHttpSession) session))
                    .andExpect(status().isUnauthorized());
        } else {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized());
        }
    }
}
