package collabdesk.project;

import collabdesk.TestcontainersConfiguration;
import collabdesk.testing.LocalAccountTestSupport;
import collabdesk.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectIntegrationTest {

    private static final String WORKSPACES_URL = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void memberCreatesAndReadsPersistentProject() throws Exception {
        MockHttpSession session = registerAndLogin(
                "project-owner@test.com",
                "Project Owner"
        );
        Long workspaceId = createWorkspace(
                session,
                "Project workspace",
                "Workspace for projects"
        );
        String projectsUrl = projectsUrl(workspaceId);

        createProject(
                session,
                projectsUrl,
                "  CollabDesk MVP  ",
                "  First working version  "
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.name").value("CollabDesk MVP"))
                .andExpect(jsonPath("$.description").value("First working version"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertProjectListContainsCreatedProject(
                session,
                projectsUrl,
                workspaceId
        );

        // The repeated request represents loading the same data after F5.
        assertProjectListContainsCreatedProject(
                session,
                projectsUrl,
                workspaceId
        );
    }

    @Test
    void anotherAuthenticatedUserCannotReadOrCreateProject() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(
                "private-project-owner@test.com",
                "Private Owner"
        );
        Long workspaceId = createWorkspace(
                ownerSession,
                "Private project workspace",
                "Private"
        );
        String projectsUrl = projectsUrl(workspaceId);
        createProject(
                ownerSession,
                projectsUrl,
                "Owner project",
                null
        ).andExpect(status().isCreated());

        MockHttpSession otherSession = registerAndLogin(
                "project-intruder@test.com",
                "Other User"
        );

        mockMvc.perform(get(projectsUrl).session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(
                        "Workspace was not found or is not accessible"
                ));

        createProject(
                otherSession,
                projectsUrl,
                "Forbidden project",
                null
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"));

        mockMvc.perform(get(projectsUrl).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Owner project"));
    }

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mockMvc.perform(get(projectsUrl(999L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPostWithoutCsrfIsForbidden() throws Exception {
        MockHttpSession session = registerAndLogin(
                "project-no-csrf@test.com",
                "No CSRF"
        );
        Long workspaceId = createWorkspace(
                session,
                "CSRF workspace",
                null
        );

        mockMvc.perform(
                post(projectsUrl(workspaceId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Project without CSRF",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isForbidden());
    }

    @Test
    void invalidNameReturnsValidationError() throws Exception {
        MockHttpSession session = registerAndLogin(
                "project-validation@test.com",
                "Validation User"
        );
        Long workspaceId = createWorkspace(
                session,
                "Validation workspace",
                null
        );

        createProject(
                session,
                projectsUrl(workspaceId),
                " ",
                "Invalid project"
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    void memberReceivesEmptyArrayWhenWorkspaceHasNoProjects() throws Exception {
        MockHttpSession session = registerAndLogin(
                "empty-projects@test.com",
                "Empty Projects"
        );
        Long workspaceId = createWorkspace(
                session,
                "Empty workspace",
                null
        );

        mockMvc.perform(get(projectsUrl(workspaceId)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private MockHttpSession registerAndLogin(
            String email,
            String displayName
    ) throws Exception {
        return LocalAccountTestSupport.registerAndLogin(
                mockMvc,
                userRepository,
                email,
                displayName,
                PASSWORD
        );
    }

    private Long createWorkspace(
            MockHttpSession session,
            String name,
            String description
    ) throws Exception {
        String descriptionJson = description == null
                ? "null"
                : "\"" + description + "\"";
        MvcResult result = mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": %s
                                }
                                """.formatted(name, descriptionJson))
        )
                .andExpect(status().isCreated())
                .andReturn();

        Number workspaceId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return workspaceId.longValue();
    }

    private ResultActions createProject(
            MockHttpSession session,
            String projectsUrl,
            String name,
            String description
    ) throws Exception {
        String descriptionJson = description == null
                ? "null"
                : "\"" + description + "\"";
        return mockMvc.perform(
                post(projectsUrl)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": %s
                                }
                                """.formatted(name, descriptionJson))
        );
    }

    private void assertProjectListContainsCreatedProject(
            MockHttpSession session,
            String projectsUrl,
            Long workspaceId
    ) throws Exception {
        mockMvc.perform(get(projectsUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$[0].name").value("CollabDesk MVP"))
                .andExpect(jsonPath("$[0].description").value(
                        "First working version"
                ))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    private String projectsUrl(Long workspaceId) {
        return WORKSPACES_URL + "/" + workspaceId + "/projects";
    }
}
