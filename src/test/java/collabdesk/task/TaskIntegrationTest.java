package collabdesk.task;

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
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String WORKSPACES_URL = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void memberCreatesReadsAndChangesPersistentTask() throws Exception {
        MockHttpSession session = registerAndLogin(
                "task-flow@test.com",
                "Task Flow"
        );
        Long workspaceId = createWorkspace(session, "Task workspace");
        Long projectId = createProject(
                session,
                workspaceId,
                "Task project"
        );
        String tasksUrl = tasksUrl(workspaceId, projectId);

        MvcResult createResult = createTask(
                session,
                tasksUrl,
                "  Implement TaskBoard  ",
                "  Add three status columns  "
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value("Implement TaskBoard"))
                .andExpect(jsonPath("$.description").value(
                        "Add three status columns"
                ))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.createdById").isNumber())
                .andExpect(jsonPath("$.createdByDisplayName").value("Task Flow"))
                .andExpect(jsonPath("$.createdByEmail").value("task-flow@test.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();
        Long taskId = idFrom(createResult);

        assertSingleTask(
                session,
                tasksUrl,
                taskId,
                "TODO"
        );
        // Repeated GET represents an F5 reload.
        assertSingleTask(
                session,
                tasksUrl,
                taskId,
                "TODO"
        );

        changeStatus(
                session,
                tasksUrl,
                taskId,
                "IN_PROGRESS",
                true
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get(tasksUrl + "/" + taskId + "/activities")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].actorDisplayName").value("Task Flow"))
                .andExpect(jsonPath("$[0].oldValue").value("TODO"))
                .andExpect(jsonPath("$[0].newValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].type").value("CREATED"));

        assertSingleTask(
                session,
                tasksUrl,
                taskId,
                "IN_PROGRESS"
        );
    }

    @Test
    void memberCanClaimManageReleaseTaskAndActivityRecordsEveryAction()
            throws Exception {
        MockHttpSession ownerSession = registerAndLogin(
                "claim-owner@test.com",
                "Claim Owner"
        );
        MockHttpSession memberSession = registerAndLogin(
                "claim-member@test.com",
                "Claim Member"
        );
        Long workspaceId = createWorkspace(ownerSession, "Claim workspace");
        addWorkspaceMember(
                ownerSession,
                workspaceId,
                "claim-member@test.com",
                "MEMBER"
        );
        Long projectId = createProject(ownerSession, workspaceId, "Claim project");
        String tasksUrl = tasksUrl(workspaceId, projectId);
        Long taskId = idFrom(createTask(
                ownerSession,
                tasksUrl,
                "Unassigned work",
                null
        ).andExpect(status().isCreated()).andReturn());

        changeStatus(memberSession, tasksUrl, taskId, "IN_PROGRESS", true)
                .andExpect(status().isForbidden());

        mockMvc.perform(put(tasksUrl + "/" + taskId + "/claim")
                        .session(memberSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.displayName").value("Claim Member"));

        changeStatus(memberSession, tasksUrl, taskId, "IN_PROGRESS", true)
                .andExpect(status().isOk());

        mockMvc.perform(delete(tasksUrl + "/" + taskId + "/claim")
                        .session(memberSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());

        changeStatus(memberSession, tasksUrl, taskId, "DONE", true)
                .andExpect(status().isForbidden());

        mockMvc.perform(get(tasksUrl + "/" + taskId + "/activities")
                        .session(memberSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(tasksUrl + "/" + taskId + "/activities")
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RELEASED"))
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[2].type").value("CLAIMED"))
                .andExpect(jsonPath("$[3].type").value("CREATED"));
    }

    @Test
    void emptyProjectReturnsEmptyArray() throws Exception {
        MockHttpSession session = registerAndLogin(
                "empty-task@test.com",
                "Empty Task"
        );
        Long workspaceId = createWorkspace(session, "Empty task workspace");
        Long projectId = createProject(
                session,
                workspaceId,
                "Empty task project"
        );

        mockMvc.perform(get(tasksUrl(workspaceId, projectId)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void nonMemberCannotReadCreateOrChangeTasks() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(
                "task-owner@test.com",
                "Task Owner"
        );
        Long workspaceId = createWorkspace(
                ownerSession,
                "Private task workspace"
        );
        Long projectId = createProject(
                ownerSession,
                workspaceId,
                "Private task project"
        );
        String tasksUrl = tasksUrl(workspaceId, projectId);
        Long taskId = idFrom(
                createTask(
                        ownerSession,
                        tasksUrl,
                        "Owner task",
                        null
                ).andExpect(status().isCreated()).andReturn()
        );

        MockHttpSession otherSession = registerAndLogin(
                "task-intruder@test.com",
                "Task Intruder"
        );

        mockMvc.perform(get(tasksUrl).session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"));
        createTask(
                otherSession,
                tasksUrl,
                "Forbidden task",
                null
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"));
        changeStatus(
                otherSession,
                tasksUrl,
                taskId,
                "DONE",
                true
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"));

        assertSingleTask(ownerSession, tasksUrl, taskId, "TODO");
    }

    @Test
    void projectCannotBeUsedThroughAnotherWorkspaceUrl() throws Exception {
        MockHttpSession session = registerAndLogin(
                "task-project-scope@test.com",
                "Project Scope"
        );
        Long firstWorkspaceId = createWorkspace(
                session,
                "First scoped workspace"
        );
        Long secondWorkspaceId = createWorkspace(
                session,
                "Second scoped workspace"
        );
        Long secondProjectId = createProject(
                session,
                secondWorkspaceId,
                "Second scoped project"
        );

        mockMvc.perform(
                get(tasksUrl(firstWorkspaceId, secondProjectId))
                        .session(session)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Project not found"));
    }

    @Test
    void taskCannotBeChangedThroughAnotherProjectUrl() throws Exception {
        MockHttpSession session = registerAndLogin(
                "task-scope@test.com",
                "Task Scope"
        );
        Long workspaceId = createWorkspace(
                session,
                "Task scope workspace"
        );
        Long firstProjectId = createProject(
                session,
                workspaceId,
                "First scope project"
        );
        Long secondProjectId = createProject(
                session,
                workspaceId,
                "Second scope project"
        );
        String secondTasksUrl = tasksUrl(workspaceId, secondProjectId);
        Long secondTaskId = idFrom(
                createTask(
                        session,
                        secondTasksUrl,
                        "Second project task",
                        null
                ).andExpect(status().isCreated()).andReturn()
        );

        changeStatus(
                session,
                tasksUrl(workspaceId, firstProjectId),
                secondTaskId,
                "DONE",
                true
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Task not found"));

        assertSingleTask(
                session,
                secondTasksUrl,
                secondTaskId,
                "TODO"
        );
    }

    @Test
    void anonymousAndMissingCsrfRequestsAreRejected() throws Exception {
        mockMvc.perform(get(tasksUrl(999L, 999L)))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = registerAndLogin(
                "task-csrf@test.com",
                "Task CSRF"
        );
        Long workspaceId = createWorkspace(session, "Task CSRF workspace");
        Long projectId = createProject(
                session,
                workspaceId,
                "Task CSRF project"
        );
        String tasksUrl = tasksUrl(workspaceId, projectId);

        createTaskWithoutCsrf(session, tasksUrl, "Missing CSRF")
                .andExpect(status().isForbidden());

        Long taskId = idFrom(
                createTask(
                        session,
                        tasksUrl,
                        "Protected task",
                        null
                ).andExpect(status().isCreated()).andReturn()
        );
        changeStatus(
                session,
                tasksUrl,
                taskId,
                "DONE",
                false
        ).andExpect(status().isForbidden());
    }

    @Test
    void invalidTitleAndStatusesReturnBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin(
                "task-validation@test.com",
                "Task Validation"
        );
        Long workspaceId = createWorkspace(
                session,
                "Task validation workspace"
        );
        Long projectId = createProject(
                session,
                workspaceId,
                "Task validation project"
        );
        String tasksUrl = tasksUrl(workspaceId, projectId);

        createTask(session, tasksUrl, " ", "Invalid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").isNotEmpty());

        Long taskId = idFrom(
                createTask(
                        session,
                        tasksUrl,
                        "Valid task",
                        null
                ).andExpect(status().isCreated()).andReturn()
        );

        changeStatus(
                session,
                tasksUrl,
                taskId,
                null,
                true
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").isNotEmpty());

        changeStatus(
                session,
                tasksUrl,
                taskId,
                "UNKNOWN",
                true
        ).andExpect(status().isBadRequest());
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

    private Long createWorkspace(
            MockHttpSession session,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                post(WORKSPACES_URL)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": null
                                }
                                """.formatted(name))
        )
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(result);
    }

    private Long createProject(
            MockHttpSession session,
            Long workspaceId,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                post(WORKSPACES_URL + "/" + workspaceId + "/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": null
                                }
                                """.formatted(name))
        )
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(result);
    }

    private void addWorkspaceMember(
            MockHttpSession ownerSession,
            Long workspaceId,
            String email,
            String role
    ) throws Exception {
        mockMvc.perform(post(WORKSPACES_URL + "/" + workspaceId + "/members")
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "role": "%s"
                                }
                                """.formatted(email, role)))
                .andExpect(status().isCreated());
    }

    private ResultActions createTask(
            MockHttpSession session,
            String tasksUrl,
            String title,
            String description
    ) throws Exception {
        return mockMvc.perform(
                post(tasksUrl)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": %s
                                }
                                """.formatted(title, jsonString(description)))
        );
    }

    private ResultActions createTaskWithoutCsrf(
            MockHttpSession session,
            String tasksUrl,
            String title
    ) throws Exception {
        return mockMvc.perform(
                post(tasksUrl)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": null
                                }
                                """.formatted(title))
        );
    }

    private ResultActions changeStatus(
            MockHttpSession session,
            String tasksUrl,
            Long taskId,
            String statusValue,
            boolean includeCsrf
    ) throws Exception {
        var request = patch(tasksUrl + "/" + taskId + "/status")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": %s
                        }
                        """.formatted(jsonString(statusValue)));
        if (includeCsrf) {
            request.with(csrf());
        }
        return mockMvc.perform(request);
    }

    private void assertSingleTask(
            MockHttpSession session,
            String tasksUrl,
            Long taskId,
            String statusValue
    ) throws Exception {
        mockMvc.perform(get(tasksUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].status").value(statusValue));
    }

    private Long idFrom(MvcResult result) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return id.longValue();
    }

    private String tasksUrl(Long workspaceId, Long projectId) {
        return WORKSPACES_URL
                + "/" + workspaceId
                + "/projects/" + projectId
                + "/tasks";
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
