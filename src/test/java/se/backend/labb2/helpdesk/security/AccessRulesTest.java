package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentOnlyService agentOnlyService;

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void userCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotOpenAgentPages() throws Exception {
        mockMvc.perform(get("/agent/tickets").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void encodedPathDoesNotBypassAdminRule() throws Exception {
        // URI.create keeps %61 as is; a plain String would be re-encoded to %2561.
        mockMvc.perform(get(URI.create("/%61dmin/users")).with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlistedPathIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/random").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInheritsAgentAccess() throws Exception {
        // The page now renders the nav, which shows displayName, so the principal must be
        // a CurrentUser (as in the real app). Id 99 isn't in the database: the list is just empty.
        CurrentUser admin = new CurrentUser(99L, "admin", "unused-hash", "Admin Test", Role.ADMIN, true);

        mockMvc.perform(get("/agent/tickets").with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/logout").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void hierarchyAppliesToPreAuthorize() {
        assertThat(agentOnlyService.ping()).isEqualTo("ok");
    }

    @Test
    @WithMockUser(roles = "USER")
    void preAuthorizeStillBlocksUser() {
        assertThatThrownBy(() -> agentOnlyService.ping())
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // Test-only bean: there are no real @PreAuthorize service methods yet.
    @TestConfiguration
    static class MethodSecurityTestConfig {
        @Bean
        AgentOnlyService agentOnlyService() {
            return new AgentOnlyService();
        }
    }

    static class AgentOnlyService {
        @PreAuthorize("hasRole('AGENT')")
        String ping() {
            return "ok";
        }
    }
}
