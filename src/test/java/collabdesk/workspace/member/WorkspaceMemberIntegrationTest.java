package collabdesk.workspace.member;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkspaceMemberIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String WORKSPACES_URL = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownerManagesMemberAndMembershipImmediatelyControlsAccess()
            throws Exception {
        String ownerEmail = "member-flow-owner@test.com";
        String memberEmail = "member-flow-user@test.com";
        MockHttpSession ownerSession =
                registerAndLogin(ownerEmail, "Flow Owner");
        register(memberEmail, "Flow Member");
        Long workspaceId = createWorkspace(
                ownerSession,
                "Member flow workspace"
        );
        String membersUrl = membersUrl(workspaceId);

        MvcResult ownerList = mockMvc.perform(
                get(membersUrl).session(ownerSession)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(ownerEmail))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andReturn();
        Long ownerMemberId = idFrom(ownerList, "$[0].id");

        MvcResult addResult = addMember(
                ownerSession,
                membersUrl,
                "  MEMBER-FLOW-USER@TEST.COM  ",
                "MEMBER",
                true
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(memberEmail))
                .andExpect(jsonPath("$.displayName").value("Flow Member"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andReturn();
        Long memberId = idFrom(addResult, "$.id");

        addMember(
                ownerSession,
                membersUrl,
                memberEmail,
                "MEMBER",
                true
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Workspace member already exists"));

        addMember(
                ownerSession,
                membersUrl,
                "unknown-member@test.com",
                "MEMBER",
                true
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));

        MockHttpSession memberSession = login(memberEmail);
        mockMvc.perform(get(WORKSPACES_URL).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(workspaceId))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));
        mockMvc.perform(get(membersUrl).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(
                patch(membersUrl + "/" + memberId + "/role")
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
        mockMvc.perform(get(WORKSPACES_URL).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("VIEWER"));

        mockMvc.perform(
                patch(membersUrl + "/" + ownerMemberId + "/role")
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}")
        )
                .andExpect(status().isConflict());
        mockMvc.perform(
                delete(membersUrl + "/" + ownerMemberId)
                        .session(ownerSession)
                        .with(csrf())
        )
                .andExpect(status().isConflict());

        mockMvc.perform(
                delete(membersUrl + "/" + memberId)
                        .session(ownerSession)
                        .with(csrf())
        ).andExpect(status().isNoContent());

        mockMvc.perform(get(WORKSPACES_URL).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get(membersUrl).session(memberSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workspace not found"));
    }

    @Test
    void viewerReadsProjectsAndTasksButCannotMutateThem()
            throws Exception {
        String viewerEmail = "permission-viewer@test.com";
        MockHttpSession ownerSession = registerAndLogin(
                "permission-owner@test.com",
                "Permission Owner"
        );
        register(viewerEmail, "Permission Viewer");
        Long workspaceId = createWorkspace(
                ownerSession,
                "Permission workspace"
        );
        Long projectId = createProject(
                ownerSession,
                workspaceId,
                "Permission project"
        );
        String tasksUrl = tasksUrl(workspaceId, projectId);
        Long taskId = createTask(
                ownerSession,
                tasksUrl,
                "Permission task"
        );
        addMember(
                ownerSession,
                membersUrl(workspaceId),
                viewerEmail,
                "VIEWER",
                true
        ).andExpect(status().isCreated());
        MockHttpSession viewerSession = login(viewerEmail);

        mockMvc.perform(
                get(WORKSPACES_URL + "/" + workspaceId + "/projects")
                        .session(viewerSession)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(tasksUrl).session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("TODO"));

        mockMvc.perform(
                post(WORKSPACES_URL + "/" + workspaceId + "/projects")
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Forbidden project",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                post(tasksUrl)
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Forbidden task",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/status")
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}")
        ).andExpect(status().isForbidden());

        mockMvc.perform(
                get(WORKSPACES_URL + "/" + workspaceId + "/projects")
                        .session(ownerSession)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(tasksUrl).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }

    @Test
    void nonOwnerCannotManageMembersAndScopedLookupRejectsForeignMember()
            throws Exception {
        String memberEmail = "forbidden-member@test.com";
        MockHttpSession ownerSession = registerAndLogin(
                "forbidden-owner@test.com",
                "Forbidden Owner"
        );
        register(memberEmail, "Forbidden Member");
        Long firstWorkspaceId = createWorkspace(
                ownerSession,
                "First member scope"
        );
        Long secondWorkspaceId = createWorkspace(
                ownerSession,
                "Second member scope"
        );
        MvcResult firstAdd = addMember(
                ownerSession,
                membersUrl(firstWorkspaceId),
                memberEmail,
                "MEMBER",
                true
        ).andExpect(status().isCreated()).andReturn();
        Long firstMemberId = idFrom(firstAdd, "$.id");
        MockHttpSession memberSession = login(memberEmail);

        addMember(
                memberSession,
                membersUrl(firstWorkspaceId),
                "nobody@test.com",
                "MEMBER",
                true
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                patch(membersUrl(firstWorkspaceId)
                        + "/" + firstMemberId + "/role")
                        .session(memberSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}")
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                delete(membersUrl(firstWorkspaceId) + "/" + firstMemberId)
                        .session(memberSession)
                        .with(csrf())
        ).andExpect(status().isForbidden());

        MvcResult secondOwnerList = mockMvc.perform(
                get(membersUrl(secondWorkspaceId)).session(ownerSession)
        ).andExpect(status().isOk()).andReturn();
        Long secondOwnerMemberId = idFrom(secondOwnerList, "$[0].id");

        mockMvc.perform(
                patch(membersUrl(firstWorkspaceId)
                        + "/" + secondOwnerMemberId + "/role")
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}")
        ).andExpect(status().isNotFound());
    }

    @Test
    void membersEndpointsEnforceAuthenticationCsrfAndValidation()
            throws Exception {
        MockHttpSession ownerSession = registerAndLogin(
                "member-security-owner@test.com",
                "Security Owner"
        );
        Long workspaceId = createWorkspace(
                ownerSession,
                "Member security workspace"
        );
        String membersUrl = membersUrl(workspaceId);
        MvcResult ownerList = mockMvc.perform(
                get(membersUrl).session(ownerSession)
        ).andExpect(status().isOk()).andReturn();
        Long ownerMemberId = idFrom(ownerList, "$[0].id");

        mockMvc.perform(get(membersUrl))
                .andExpect(status().isUnauthorized());
        addMember(
                ownerSession,
                membersUrl,
                "missing@test.com",
                "MEMBER",
                false
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                patch(membersUrl + "/" + ownerMemberId + "/role")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}")
        ).andExpect(status().isForbidden());
        mockMvc.perform(
                delete(membersUrl + "/" + ownerMemberId)
                        .session(ownerSession)
        ).andExpect(status().isForbidden());

        addMember(
                ownerSession,
                membersUrl,
                "not-an-email",
                "MEMBER",
                true
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
        addMember(
                ownerSession,
                membersUrl,
                "missing@test.com",
                null,
                true
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").isNotEmpty());
        mockMvc.perform(
                post(membersUrl)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@test.com",
                                  "role": "SUPERUSER"
                                }
                                """)
        ).andExpect(status().isBadRequest());
        addMember(
                ownerSession,
                membersUrl,
                "missing@test.com",
                "OWNER",
                true
        ).andExpect(status().isConflict());
    }

    private void register(String email, String displayName) throws Exception {
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
    }

    private MockHttpSession registerAndLogin(
            String email,
            String displayName
    ) throws Exception {
        register(email, displayName);
        return login(email);
    }

    private MockHttpSession login(String email) throws Exception {
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
        ).andExpect(status().isCreated()).andReturn();
        return idFrom(result, "$.id");
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
        ).andExpect(status().isCreated()).andReturn();
        return idFrom(result, "$.id");
    }

    private Long createTask(
            MockHttpSession session,
            String tasksUrl,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                post(tasksUrl)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": null
                                }
                                """.formatted(title))
        ).andExpect(status().isCreated()).andReturn();
        return idFrom(result, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            MockHttpSession session,
            String membersUrl,
            String email,
            String role,
            boolean includeCsrf
    ) throws Exception {
        var request = post(membersUrl)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "role": %s
                        }
                        """.formatted(
                        email,
                        role == null ? "null" : "\"" + role + "\""
                ));
        if (includeCsrf) {
            request.with(csrf());
        }
        return mockMvc.perform(request);
    }

    private Long idFrom(MvcResult result, String path) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                path
        );
        return id.longValue();
    }

    private String membersUrl(Long workspaceId) {
        return WORKSPACES_URL + "/" + workspaceId + "/members";
    }

    private String tasksUrl(Long workspaceId, Long projectId) {
        return WORKSPACES_URL
                + "/" + workspaceId
                + "/projects/" + projectId
                + "/tasks";
    }
}
