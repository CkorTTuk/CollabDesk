package collabdesk.project.role;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessRoleIntegrationTest {

    private static final String WORKSPACES = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void customRolesReplaceAndRestoreDefaultPermissions() throws Exception {
        MockHttpSession owner = registerAndLogin(
                "access-role-owner@test.com",
                "Role Owner"
        );
        MockHttpSession member = registerAndLogin(
                "access-role-member@test.com",
                "Role Member"
        );
        owner = login("access-role-owner@test.com");

        Long workspaceId = idFrom(mockMvc.perform(
                post(WORKSPACES)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Role workspace",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());
        String overviewUrl = WORKSPACES + "/" + workspaceId
                + "/project-access-overview";
        mockMvc.perform(get(overviewUrl))
                .andExpect(status().isUnauthorized());

        Long workspaceMemberId = idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/members")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "access-role-member@test.com",
                                  "role": "MEMBER"
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        String rolesUrl = WORKSPACES + "/" + workspaceId + "/access-roles";
        mockMvc.perform(get(rolesUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        Long reviewerRoleId = idFrom(mockMvc.perform(
                post(rolesUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Reviewer",
                                  "color": "#4f7dF3",
                                  "permissions": ["EDIT_PROJECT"]
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#4F7DF3"))
                .andExpect(jsonPath("$.permissions[0]")
                        .value("EDIT_PROJECT"))
                .andReturn());

        mockMvc.perform(
                post(rolesUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " reviewer ",
                                  "color": "#2AA876",
                                  "permissions": []
                                }
                                """)
        ).andExpect(status().isConflict());

        Long projectId = idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/projects")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Role project",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        String membersUrl = WORKSPACES + "/" + workspaceId
                + "/projects/" + projectId + "/members";
        Long projectMemberId = idFrom(mockMvc.perform(
                post(membersUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceMemberId": %d,
                                  "roleIds": []
                                }
                                """.formatted(workspaceMemberId))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(0))
                .andExpect(jsonPath("$.effectivePermissions.length()")
                        .value(0))
                .andReturn());

        String tasksUrl = WORKSPACES + "/" + workspaceId
                + "/projects/" + projectId + "/tasks";
        mockMvc.perform(
                post(tasksUrl)
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Default permission task",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                put(WORKSPACES + "/" + workspaceId + "/members/"
                        + workspaceMemberId + "/access-roles")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleIds": [%d]
                                }
                                """.formatted(reviewerRoleId))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Reviewer"));

        mockMvc.perform(
                put(WORKSPACES + "/" + workspaceId + "/projects/"
                        + projectId + "/allowed-roles")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds": [%d]}
                                """.formatted(reviewerRoleId))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Reviewer"));

        mockMvc.perform(get(overviewUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].members[1].displayName")
                        .value("Role Member"))
                .andExpect(jsonPath("$.projects[0].members[1].roles[0].name")
                        .value("Reviewer"))
                .andExpect(jsonPath(
                        "$.projects[0].members[1].effectivePermissions"
                ).doesNotExist())
                .andExpect(jsonPath("$.projects[0].members[1].joinedAt")
                        .doesNotExist());

        mockMvc.perform(
                post(tasksUrl)
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Forbidden task",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated());

        Long taskId = idFrom(mockMvc.perform(
                post(tasksUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Review task",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(
                patch(tasksUrl + "/" + taskId)
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Forbidden edit",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isForbidden());

        mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/status")
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """)
        ).andExpect(status().isForbidden());

        mockMvc.perform(
                put(tasksUrl + "/" + taskId + "/assignee")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectMemberId": %d
                                }
                                """.formatted(projectMemberId))
        ).andExpect(status().isOk());

        mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/status")
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                patch(tasksUrl + "/" + taskId + "/visibility")
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility": "ASSIGNEES"}
                                """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                get(tasksUrl + "/" + taskId + "/activities")
                        .session(member)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("VISIBILITY_CHANGED"))
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[2].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[3].type").value("CREATED"));

        mockMvc.perform(
                delete(rolesUrl + "/" + reviewerRoleId)
                        .session(owner)
                        .with(csrf())
        ).andExpect(status().isConflict());

        mockMvc.perform(
                put(WORKSPACES + "/" + workspaceId + "/members/"
                        + workspaceMemberId + "/access-roles")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds": []}
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(
                put(WORKSPACES + "/" + workspaceId + "/projects/"
                        + projectId + "/allowed-roles")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds": []}
                                """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                delete(rolesUrl + "/" + reviewerRoleId)
                        .session(owner)
                        .with(csrf())
        ).andExpect(status().isNoContent());
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

    private MockHttpSession login(String email) throws Exception {
        return LocalAccountTestSupport.login(mockMvc, email, PASSWORD);
    }

    private static Long idFrom(MvcResult result) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return id.longValue();
    }
}
