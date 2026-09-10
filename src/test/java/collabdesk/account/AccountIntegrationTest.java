package collabdesk.account;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.verification.VerificationCodeGenerator;
import collabdesk.testing.LocalAccountTestSupport;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountIntegrationTest {
    private static final String URL = "/api/v1/account";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @MockitoBean VerificationCodeGenerator verificationCodeGenerator;

    @BeforeEach
    void setUpVerificationCode() {
        when(verificationCodeGenerator.generate()).thenReturn("123456");
    }

    @Test
    void readsAndUpdatesCanonicalAccountAndSession() throws Exception {
        MockHttpSession session = LocalAccountTestSupport.registerAndLogin(
                mockMvc, userRepository, "account@example.com", "Initial", "password123"
        );

        mockMvc.perform(get(URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocale").value("en"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.providers").isEmpty());

        mockMvc.perform(patch(URL + "/profile").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":" Ada ","lastName":" Lovelace ","birthDate":"1815-12-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));

        mockMvc.perform(patch(URL + "/locale").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"sk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLocale").value("sk"));
    }

    @Test
    void protectsAccountEndpoints() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        MockHttpSession session = LocalAccountTestSupport.registerAndLogin(
                mockMvc, userRepository, "csrf-account@example.com", "Member", "password123"
        );
        mockMvc.perform(patch(URL + "/locale").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"ru\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadsReadsAndRemovesNormalizedAvatar() throws Exception {
        MockHttpSession session = LocalAccountTestSupport.registerAndLogin(
                mockMvc, userRepository, "avatar-account@example.com", "Avatar User", "password123"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "client-name.png", "application/octet-stream", png()
        );

        mockMvc.perform(multipart(URL + "/avatar").file(file).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty());

        String key = userRepository.findByEmail("avatar-account@example.com")
                .orElseThrow().getAvatarKey();
        mockMvc.perform(get("/api/v1/avatars/{key}", key).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        "nosniff", result.getResponse().getHeader("X-Content-Type-Options")
                ));

        mockMvc.perform(delete(URL + "/avatar").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        mockMvc.perform(get("/api/v1/avatars/{key}", key).session(session))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart(URL + "/avatar")
                        .file(new MockMultipartFile(
                                "file", "fake.png", "image/png", "not an image".getBytes()
                        ))
                        .session(session).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("invalid_avatar"));
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
