package collabdesk.auth.security;

import collabdesk.auth.config.SecurityConfig;
import collabdesk.auth.registration.RegistrationResult;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.controller.AuthController;
import collabdesk.controller.CsrfController;
import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, CsrfController.class})
@Import(SecurityConfig.class)
class SecurityMvcTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String VALID_REGISTRATION_JSON = """
            {
              "email": "student@example.com",
              "displayName": "Student",
              "password": "password123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private LocalUserDetailsService localUserDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void csrfEndpointIsAvailableToAnonymousUser() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));

        verifyNoInteractions(registrationService);
    }

    @Test
    void anonymousRegistrationWithValidCsrfCallsService() throws Exception {
        when(registrationService.register(
                "student@example.com",
                "Student",
                "password123"
        )).thenReturn(new RegistrationResult(
                1L,
                "student@example.com",
                "Student",
                UserStatus.ACTIVE
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("student@example.com"));

        verify(registrationService).register(
                "student@example.com",
                "Student",
                "password123"
        );
    }

    @Test
    void registrationWithoutCsrfIsForbiddenBeforeController() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrationService);
    }

    @Test
    void registrationWithInvalidCsrfIsForbiddenBeforeController() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrationService);
    }

    @Test
    void anyOtherRequestRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/private-test-route"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(registrationService);
    }
}
