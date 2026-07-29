package collabdesk.openapi;

import collabdesk.TestcontainersConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiSpecificationRequiresAuthenticationAndDocumentsApi()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/v3/api-docs").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("CollabDesk API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.sessionCookie"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.parameters.csrfToken"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AddWorkspaceMemberRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AddWorkspaceMemberRequest"
                                + ".properties.role.enum.length()"
                ).value(3))
                .andExpect(jsonPath(
                        "$.components.schemas.WorkspaceMemberResponse"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CreateTaskRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.TaskResponse"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AddProjectMemberRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ProjectMemberResponse"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ReplaceTaskAssigneesRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.TaskAssigneeResponse"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateProjectVisibilityRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateProjectVisibilityRequest"
                                + ".properties.visibility.enum.length()"
                ).value(2))
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateTaskVisibilityRequest"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateTaskVisibilityRequest"
                                + ".properties.visibility.enum.length()"
                ).value(2))
                .andExpect(jsonPath(
                        "$.components.schemas.ApiProblemResponse"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/register'].post.summary"
                ).value("Register an account"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/login'].post.summary"
                ).value("Log in"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/login'].post.requestBody"
                                + ".content['application/x-www-form-urlencoded']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/logout'].post.summary"
                ).value("Log out"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces'].post.parameters[0]['$ref']"
                ).value("#/components/parameters/csrfToken"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/members'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/members/{memberId}/role'].patch"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/status'].patch"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{projectMemberId}'].delete"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/assignees'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/visibility'].patch"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/visibility'].patch"
                ).exists())
                .andExpect(jsonPath("$.paths['/csrf'].get").exists());
    }

    @Test
    void authenticatedUserCanOpenYamlAndSwaggerUi() throws Exception {
        MockHttpSession session = registerAndLogin(
                "openapi-ui@test.com",
                "OpenAPI UI"
        );

        mockMvc.perform(get("/v3/api-docs.yaml").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "title: CollabDesk API"
                        )
                ));

        mockMvc.perform(get("/swagger-ui.html").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "/swagger-ui/index.html"
                        )
                ));
    }

    private MockHttpSession registerAndLogin() throws Exception {
        return registerAndLogin(
                "openapi-spec@test.com",
                "OpenAPI Spec"
        );
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
}
