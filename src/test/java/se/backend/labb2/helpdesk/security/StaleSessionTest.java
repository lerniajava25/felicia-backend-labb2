package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an open session remembers after an admin changes the user. Real sessions from the
 * login form, not with(user(...)), because the point is what is kept between requests.
 * Without CurrentUserRefreshFilter every one of these would still use the old role.
 */
// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StaleSessionTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User boss;
    private User olle;
    private User billy;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));
        boss = userRepository.save(new User("boss", hash, "Boss Test", Role.ADMIN));
        olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        userRepository.flush();
    }

    /** Logs in with the real login form and returns the session it created. */
    private MockHttpSession logIn(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(TEST_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    /** The admin saves the edit form for the target, as the browser sends it. */
    private void adminSaves(User target, Role role, boolean enabled) throws Exception {
        var request = post("/admin/users/{id}", target.getId())
                .param("role", role.name())
                .param("_enabled", "on")
                .param("version", target.getVersion().toString())
                .with(user(CurrentUser.from(admin)))
                .with(csrf());
        if (enabled) {
            request.param("enabled", "true");
        }
        mockMvc.perform(request).andExpect(redirectedUrl("/admin/users"));
        userRepository.flush();
    }

    @Test
    void demotedAdminLosesAdminPagesInTheSameSession() throws Exception {
        MockHttpSession bossSession = logIn("boss");
        mockMvc.perform(get("/admin/users").session(bossSession)).andExpect(status().isOk());

        adminSaves(boss, Role.AGENT, true);

        mockMvc.perform(get("/admin/users").session(bossSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/agent/tickets/mine").session(bossSession)).andExpect(status().isOk());
        mockMvc.perform(get("/").session(bossSession))
                .andExpect(content().string(containsString("(agent)")));
    }

    @Test
    void disabledUserIsLoggedOutAtTheNextRequest() throws Exception {
        MockHttpSession billySession = logIn("billy");
        mockMvc.perform(get("/tickets").session(billySession)).andExpect(status().isOk());

        adminSaves(billy, Role.USER, false);

        mockMvc.perform(get("/tickets").session(billySession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertThat(billySession.isInvalid()).isTrue();
    }

    @Test
    void promotedUserGetsTheNewRoleWithoutLoggingInAgain() throws Exception {
        MockHttpSession olleSession = logIn("olle");
        mockMvc.perform(get("/agent/tickets/mine").session(olleSession)).andExpect(status().isForbidden());

        adminSaves(olle, Role.AGENT, true);

        mockMvc.perform(get("/agent/tickets/mine").session(olleSession)).andExpect(status().isOk());
    }

    @Test
    void changingOneUserLeavesOtherSessionsAlone() throws Exception {
        MockHttpSession billySession = logIn("billy");

        adminSaves(olle, Role.AGENT, false);

        mockMvc.perform(get("/tickets").session(billySession)).andExpect(status().isOk());
        mockMvc.perform(get("/agent/tickets/mine").session(billySession)).andExpect(status().isForbidden());
        assertThat(billySession.isInvalid()).isFalse();
    }
}
