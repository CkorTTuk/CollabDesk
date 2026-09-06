package collabdesk.auth.verification;

import collabdesk.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailVerificationIntegrationTest {
    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String CONFIRM_URL =
            "/api/v1/auth/email-verification/confirm";
    private static final String RESEND_URL =
            "/api/v1/auth/email-verification/resend";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerificationCodeGenerator codeGenerator;

    @BeforeEach
    void useKnownCode() {
        when(codeGenerator.generate()).thenReturn("004271");
    }

    @Test
    void correctCodeVerifiesEmailAndCreatesAuthenticatedSession()
            throws Exception {
        register("verified-by-code@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());

        MvcResult confirmResult = mockMvc.perform(post(CONFIRM_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "verified-by-code@example.com",
                                  "code": "004271"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andReturn();

        MockHttpSession session = (MockHttpSession) confirmResult
                .getRequest()
                .getSession(false);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email")
                        .value("verified-by-code@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void thirdWrongCodeExhaustsChallengeAndFailureCountIsCommitted()
            throws Exception {
        register("three-attempts@example.com")
                .andExpect(status().isCreated());

        confirmWrongCode("three-attempts@example.com")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code")
                        .value("invalid_or_expired_code"));
        confirmWrongCode("three-attempts@example.com")
                .andExpect(status().isUnprocessableContent());
        confirmWrongCode("three-attempts@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("verification_attempts_exhausted"));

        mockMvc.perform(post(CONFIRM_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "three-attempts@example.com",
                                  "code": "004271"
                                }
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void immediateResendIsRateLimitedWithoutRevealingUnknownAccounts()
            throws Exception {
        register("cooldown@example.com").andExpect(status().isCreated());

        resend("cooldown@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        resend("not-registered@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());
    }

    @Test
    void confirmationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "code": "004271"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email
    ) throws Exception {
        return mockMvc.perform(post(REGISTER_URL)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "password123",
                          "passwordConfirmation": "password123"
                        }
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions confirmWrongCode(
            String email
    ) throws Exception {
        return mockMvc.perform(post(CONFIRM_URL)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "code": "999999"
                        }
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions resend(
            String email
    ) throws Exception {
        return mockMvc.perform(post(RESEND_URL)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }
}
