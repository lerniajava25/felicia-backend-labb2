package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's users are rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    // The closing quote matters: "/tickets/new" must not count as the "/tickets" link.
    private static final String MY_TICKETS_LINK = "href=\"/tickets\"";
    private static final String NEW_TICKET_LINK = "href=\"/tickets/new\"";
    private static final String ASSIGNED_LINK = "href=\"/agent/tickets/mine\"";
    private static final String ALL_TICKETS_LINK = "href=\"/agent/tickets\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;
    private User admin;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = new User("olle", hash, "Olle Test", Role.USER);
        olle.setEnabled(false);
        userRepository.save(olle);
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));
        userRepository.flush();
    }

    @Test
    void loginPageShowsFormToAnonymous() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("action=\"/login\""),
                        containsString("name=\"username\""),
                        containsString("name=\"password\""),
                        containsString("name=\"_csrf\""))));
    }

    @Test
    void loginPageRedirectsHomeWhenAlreadyLoggedIn() throws Exception {
        mockMvc.perform(get("/login").with(user(CurrentUser.from(billy))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void correctPasswordLogsIn() throws Exception {
        mockMvc.perform(formLogin("/login").user("billy").password(TEST_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated()
                        .withUsername("billy")
                        // Spring Security 7 also adds FACTOR_PASSWORD, so check "contains", not "only".
                        .withAuthentication(auth -> assertThat(auth.getAuthorities())
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_USER")));
    }

    @Test
    void wrongPasswordFails() throws Exception {
        mockMvc.perform(formLogin("/login").user("billy").password("not-the-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        mockMvc.perform(formLogin("/login").user("olle").password(TEST_PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void logoutWithCsrfRedirectsToLoginPage() throws Exception {
        mockMvc.perform(post("/logout").with(user(CurrentUser.from(billy))).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void navForUserShowsUserRoleAndMyTickets() throws Exception {
        mockMvc.perform(get("/").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Billy Test"),
                        containsString("(user)"),
                        containsString(MY_TICKETS_LINK),
                        containsString(NEW_TICKET_LINK))))
                .andExpect(content().string(not(containsString("(agent)"))))
                .andExpect(content().string(not(containsString("(admin)"))))
                .andExpect(content().string(not(containsString(ASSIGNED_LINK))))
                .andExpect(content().string(not(containsString(ALL_TICKETS_LINK))));
    }

    @Test
    void navForAgentShowsOnlyAgentRoleAndAgentLinks() throws Exception {
        CurrentUser agent = new CurrentUser(99L, "agent", "unused-hash", "Agent Test", Role.AGENT, true);

        mockMvc.perform(get("/").with(user(agent)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(agent)")))
                .andExpect(content().string(stringContainsInOrder(
                        ALL_TICKETS_LINK, ASSIGNED_LINK, NEW_TICKET_LINK)))
                .andExpect(content().string(not(containsString("(admin)"))))
                .andExpect(content().string(not(containsString("(user)"))))
                .andExpect(content().string(not(containsString(MY_TICKETS_LINK))));
    }

    @Test
    void navForAdminShowsOnlyAdminRoleAndAgentLinks() throws Exception {
        mockMvc.perform(get("/").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("(admin)"),
                        containsString(ASSIGNED_LINK),
                        containsString(ALL_TICKETS_LINK))))
                .andExpect(content().string(not(containsString("(agent)"))))
                .andExpect(content().string(not(containsString("(user)"))))
                .andExpect(content().string(not(containsString(MY_TICKETS_LINK))));
    }

    @Test
    void navForAnonymousHasNoTicketLinks() throws Exception {
        // !hasRole('AGENT') is also true when logged out; "isAuthenticated() and" stops that.
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(MY_TICKETS_LINK))))
                .andExpect(content().string(not(containsString(ASSIGNED_LINK))));
    }
}
