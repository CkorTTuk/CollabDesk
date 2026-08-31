package collabdesk.visibility;

import collabdesk.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VisibilityIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String WORKSPACES = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void restrictedProjectsAndAssigneeOnlyTasksAreFilteredServerSide()
            throws Exception {
        MockHttpSession owner = registerAndLogin(
                "visibility-owner@test.com",
                "Visibility Owner"
        );
        MockHttpSession assignee = registerAndLogin(
                "visibility-assignee@test.com",
                "Visibility Assignee"
        );
        MockHttpSession otherMember = registerAndLogin(
                "visibility-other@test.com",
                "Visibility Other"
        );
        owner = login("visibility-owner@test.com");

        Long workspaceId = createWorkspace(owner);
        Long assigneeWorkspaceMemberId = addWorkspaceMember(
                owner,
                workspaceId,
                "visibility-assignee@test.com"
        );
        Long otherWorkspaceMemberId = addWorkspaceMember(
                owner,
                workspaceId,
                "visibility-other@test.com"
        );
        Long projectId = createProject(owner, workspaceId);
        String projectsUrl = WORKSPACES + "/" + workspaceId + "/projects";
        String projectMembersUrl = projectsUrl + "/" + projectId + "/members";
        String tasksUrl = projectsUrl + "/" + projectId + "/tasks";

        Long assigneeProjectMemberId = addProjectMember(
                owner,
                projectMembersUrl,
                assigneeWorkspaceMemberId
        );
        Long taskId = createTask(owner, tasksUrl);

        changeTaskVisibility(owner, tasksUrl, taskId, "ASSIGNEES")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Task visibility conflict"));

        mockMvc.perform(
                put(tasksUrl + "/" + taskId + "/assignee")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectMemberId": %d
                                }
                                """.formatted(assigneeProjectMemberId))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.projectMemberId")
                        .value(assigneeProjectMemberId));

        String overviewUrl = WORKSPACES + "/" + workspaceId
                + "/project-access-overview";
        mockMvc.perform(get(overviewUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].projectId").value(projectId))
                .andExpect(jsonPath("$.projects[0].members.length()").value(2))
                .andExpect(jsonPath("$.projects[0].members[0].joinedAt")
                        .doesNotExist());
        mockMvc.perform(get(overviewUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(0));

        mockMvc.perform(get(projectsUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(tasksUrl).session(otherMember))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Project not found"));

        addProjectMember(owner, projectMembersUrl, otherWorkspaceMemberId);

        mockMvc.perform(get(overviewUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].members.length()").value(3));

        mockMvc.perform(get(projectsUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].restricted").value(true));

        changeTaskVisibility(owner, tasksUrl, taskId, "ASSIGNEES")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("ASSIGNEES"));

        mockMvc.perform(get(tasksUrl).session(assignee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId));
        mockMvc.perform(get(tasksUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/status")
                        .session(otherMember)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE"
                                }
                                """)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Task not found"));

        mockMvc.perform(get(tasksUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(
                put(tasksUrl + "/" + taskId + "/assignee")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectMemberId": null
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PROJECT"))
                .andExpect(jsonPath("$.assignee").doesNotExist());

        mockMvc.perform(get(tasksUrl).session(otherMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private MockHttpSession registerAndLogin(
            String email,
            String displayName
    ) throws Exception {
        mockMvc.perform(
                post(AUTH + "/register")
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
        return login(email);
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(
                post(AUTH + "/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        ).andExpect(status().isNoContent()).andReturn();
        HttpSession session = result.getRequest().getSession(false);
        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private Long createWorkspace(MockHttpSession owner) throws Exception {
        return idFrom(mockMvc.perform(
                post(WORKSPACES)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Visibility workspace",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());
    }

    private Long addWorkspaceMember(
            MockHttpSession owner,
            Long workspaceId,
            String email
    ) throws Exception {
        return idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/members")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(email))
        ).andExpect(status().isCreated()).andReturn());
    }

    private Long createProject(
            MockHttpSession owner,
            Long workspaceId
    ) throws Exception {
        return idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/projects")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Restricted project",
                                  "description": null
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restricted").value(false))
                .andReturn());
    }

    private Long addProjectMember(
            MockHttpSession owner,
            String projectMembersUrl,
            Long workspaceMemberId
    ) throws Exception {
        return idFrom(mockMvc.perform(
                post(projectMembersUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceMemberId": %d,
                                  "roleIds": []
                                }
                                """.formatted(workspaceMemberId))
        ).andExpect(status().isCreated()).andReturn());
    }

    private Long createTask(
            MockHttpSession owner,
            String tasksUrl
    ) throws Exception {
        return idFrom(mockMvc.perform(
                post(tasksUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Private task",
                                  "description": null
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PROJECT"))
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions
    changeTaskVisibility(
            MockHttpSession owner,
            String tasksUrl,
            Long taskId,
            String visibility
    ) throws Exception {
        return mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/visibility")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visibility": "%s"
                                }
                                """.formatted(visibility))
        );
    }

    private Long idFrom(MvcResult result) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return id.longValue();
    }
}
