package collabdesk.workspace;

import collabdesk.TestcontainersConfiguration;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkspaceIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String WORKSPACES_URL = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void authenticatedUserCreatesWorkspaceAndBecomesOwnerInDatabase()
            throws Exception {
        String email = "workspace-owner@test.com";
        MockHttpSession session = registerAndLogin(email, "Workspace Owner");

        mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  CollabDesk Team  ",
                                  "description": "  Main team workspace  "
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("CollabDesk Team"))
                .andExpect(jsonPath("$.description").value("Main team workspace"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        User authenticatedUser = userRepository.findByEmail(email).orElseThrow();
        List<WorkspaceMember> memberships =
                workspaceMemberRepository
                        .findByUser_IdOrderByWorkspace_CreatedAtAsc(
                                authenticatedUser.getId()
                        );
        assertEquals(1, memberships.size());

        WorkspaceMember membership = memberships.getFirst();
        Long workspaceId = membership.getWorkspace().getId();
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();

        assertAll(
                () -> assertEquals(WorkspaceRole.OWNER, membership.getRole()),
                () -> assertEquals(
                        authenticatedUser.getId(),
                        membership.getUser().getId()
                ),
                () -> assertEquals(
                        authenticatedUser.getId(),
                        workspace.getCreatedBy().getId()
                ),
                () -> assertEquals("CollabDesk Team", workspace.getName()),
                () -> assertEquals("Main team workspace", workspace.getDescription())
        );
    }

    @Test
    void getReturnsCreatedWorkspaceOnRepeatedRequest() throws Exception {
        MockHttpSession session = registerAndLogin(
                "workspace-reload@test.com",
                "Reload User"
        );
        createWorkspace(session, "Persistent workspace", "Stored in MySQL")
                .andExpect(status().isCreated());

        assertWorkspaceListContains(
                session,
                "Persistent workspace",
                "Stored in MySQL"
        );

        // A second GET represents the request made after a frontend/F5 reload.
        assertWorkspaceListContains(
                session,
                "Persistent workspace",
                "Stored in MySQL"
        );
    }

    @Test
    void secondUserCannotSeeFirstUsersWorkspace() throws Exception {
        MockHttpSession firstSession = registerAndLogin(
                "first-workspace-user@test.com",
                "First User"
        );
        createWorkspace(firstSession, "First user's workspace", "Private")
                .andExpect(status().isCreated());

        MockHttpSession secondSession = registerAndLogin(
                "second-workspace-user@test.com",
                "Second User"
        );

        mockMvc.perform(get(WORKSPACES_URL).session(secondSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mockMvc.perform(get(WORKSPACES_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPostWithoutCsrfIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(
                post(WORKSPACES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anonymous workspace",
                                  "description": "Must not be created"
                                }
                                """)
        ).andReturn();

        int responseStatus = result.getResponse().getStatus();
        assertTrue(
                responseStatus == 401 || responseStatus == 403,
                () -> "Expected 401 or 403, but received " + responseStatus
        );
    }

    @Test
    void authenticatedPostWithoutCsrfIsForbidden() throws Exception {
        MockHttpSession session = registerAndLogin(
                "workspace-no-csrf@test.com",
                "No CSRF"
        );

        mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "No CSRF workspace",
                                  "description": "Must not be created"
                                }
                                """)
        ).andExpect(status().isForbidden());
    }

    @Test
    void invalidNameReturnsValidationError() throws Exception {
        MockHttpSession session = registerAndLogin(
                "workspace-validation@test.com",
                "Validation User"
        );

        mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "description": "Invalid workspace"
                                }
                                """)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    private MockHttpSession registerAndLogin(
            String email,
            String displayName
    ) throws Exception {
        mockMvc.perform(
                post(REGISTER_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, displayName, PASSWORD))
        ).andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(
                post(LOGIN_URL)
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        )
                .andExpect(status().isNoContent())
                .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);
        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private org.springframework.test.web.servlet.ResultActions createWorkspace(
            MockHttpSession session,
            String name,
            String description
    ) throws Exception {
        return mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "%s"
                                }
                                """.formatted(name, description))
        );
    }

    private void assertWorkspaceListContains(
            MockHttpSession session,
            String name,
            String description
    ) throws Exception {
        mockMvc.perform(get(WORKSPACES_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").value(name))
                .andExpect(jsonPath("$[0].description").value(description))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }
}
