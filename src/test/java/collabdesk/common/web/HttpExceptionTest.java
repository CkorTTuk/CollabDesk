package collabdesk.common.web;

import collabdesk.auth.registration.EmailAlreadyExistsException;
import collabdesk.auth.registration.RegistrationResult;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.auth.controller.AuthController;
import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class HttpExceptionTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void validRequestReturnsCreatedResponseAndCallsService() throws Exception {
        when(registrationService.register(
                "student@example.com",
                "password123"
        )).thenReturn(new RegistrationResult(
                1L,
                "student@example.com",
                "Student",
                UserStatus.ACTIVE,
                true,
                null,
                null
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "password123",
                                  "passwordConfirmation": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.displayName").value("Student"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(registrationService).register(
                "student@example.com",
                "password123"
        );
    }

    @Test
    void invalidEmailReturnsBadRequestAndDoesNotCallService() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "password123",
                                  "passwordConfirmation": "password123"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("must be a well-formed email address"))
                .andExpect(jsonPath("$.errors.password").doesNotExist());

        verifyNoInteractions(registrationService);
    }

    @Test
    void mismatchedPasswordsReturnBadRequestAndDoNotCallService() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "password123",
                                  "passwordConfirmation": "different123"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("passwords_do_not_match"))
                .andExpect(jsonPath("$.errors.passwordConfirmation").value("Passwords do not match"))
                .andExpect(jsonPath("$.errors.email").doesNotExist())
                .andExpect(jsonPath("$.errors.password").doesNotExist());

        verifyNoInteractions(registrationService);
    }

    @Test
    void shortPasswordReturnsBadRequestAndDoesNotCallService() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "short",
                                  "passwordConfirmation": "short"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.password").value("size must be between 8 and 64"))
                .andExpect(jsonPath("$.errors.email").doesNotExist());

        verifyNoInteractions(registrationService);
    }

    @Test
    void malformedJsonReturnsBadRequestAndDoesNotCallService() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "password123",
                                  "passwordConfirmation": "password123"
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    void duplicateEmailReturnsConflictProblemDetail() throws Exception {
        when(registrationService.register(
                "student@example.com",
                "password123"
        )).thenThrow(new EmailAlreadyExistsException(
                "there is already an account with that email"
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "password123",
                                  "passwordConfirmation": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already registered"))
                .andExpect(jsonPath("$.detail").value(
                        "An account with this email already exists"
                ));

        verify(registrationService).register(
                "student@example.com",
                "password123"
        );
    }
}
