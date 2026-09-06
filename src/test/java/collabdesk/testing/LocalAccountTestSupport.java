package collabdesk.testing;

import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class LocalAccountTestSupport {
    private static final String AUTH_URL = "/api/v1/auth";

    private LocalAccountTestSupport() {
    }

    public static void registerCompletedAccount(
            MockMvc mockMvc,
            UserRepository userRepository,
            String email,
            String displayName,
            String password
    ) throws Exception {
        mockMvc.perform(post(AUTH_URL + "/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "passwordConfirmation": "%s"
                                }
                                """.formatted(email, password, password)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.markEmailVerified(Instant.now());
        user.completeOnboarding(displayName, null, null);
        userRepository.saveAndFlush(user);
    }

    public static MockHttpSession login(
            MockMvc mockMvc,
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(AUTH_URL + "/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isNoContent())
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        return assertInstanceOf(MockHttpSession.class, session);
    }

    public static MockHttpSession registerAndLogin(
            MockMvc mockMvc,
            UserRepository userRepository,
            String email,
            String displayName,
            String password
    ) throws Exception {
        registerCompletedAccount(
                mockMvc,
                userRepository,
                email,
                displayName,
                password
        );
        return login(mockMvc, email, password);
    }
}
