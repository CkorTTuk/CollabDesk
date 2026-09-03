package collabdesk.project.member;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectMembershipIntegrationTest {

    private static final String WORKSPACES = "/api/v1/workspaces";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void ownerBuildsProjectTeamAssignsTaskAndRemovalClearsAssignment()
            throws Exception {
        MockHttpSession owner = registerAndLogin(
                "project-team-owner@test.com",
                "Team Owner"
        );
        MockHttpSession memberSession = registerAndLogin(
                "project-team-member@test.com",
                "Team Member"
        );
        owner = login("project-team-owner@test.com");

        Long workspaceId = idFrom(mockMvc.perform(
                post(WORKSPACES)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Project team workspace",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        Long workspaceMemberId = idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/members")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "project-team-member@test.com",
                                  "role": "MEMBER"
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        Long projectId = idFrom(mockMvc.perform(
                post(WORKSPACES + "/" + workspaceId + "/projects")
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Assigned project",
                                  "description": null
                                }
                                """)
        ).andExpect(status().isCreated()).andReturn());

        String membersUrl = WORKSPACES + "/" + workspaceId
                + "/projects/" + projectId + "/members";
        mockMvc.perform(get(membersUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Team Owner"));

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
                .andExpect(jsonPath("$.displayName").value("Team Member"))
                .andExpect(jsonPath("$.workspaceRole").value("MEMBER"))
                .andReturn());

        mockMvc.perform(
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
        ).andExpect(status().isConflict());

        String tasksUrl = WORKSPACES + "/" + workspaceId
                + "/projects/" + projectId + "/tasks";
        Long taskId = idFrom(mockMvc.perform(
                post(tasksUrl)
                        .session(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Assigned task",
                                  "description": null
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee").doesNotExist())
                .andReturn());

        mockMvc.perform(
                put(tasksUrl + "/" + taskId + "/assignee")
                        .session(memberSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectMemberId": %d
                                }
                                """.formatted(projectMemberId))
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
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.projectMemberId")
                        .value(projectMemberId))
                .andExpect(jsonPath("$.assignee.displayName")
                        .value("Team Member"));

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
                .andExpect(jsonPath("$.assignee").doesNotExist());

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
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.projectMemberId")
                        .value(projectMemberId));

        mockMvc.perform(
                delete(membersUrl + "/" + projectMemberId)
                        .session(owner)
                        .with(csrf())
        ).andExpect(status().isNoContent());

        mockMvc.perform(get(tasksUrl).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignee").doesNotExist());
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
