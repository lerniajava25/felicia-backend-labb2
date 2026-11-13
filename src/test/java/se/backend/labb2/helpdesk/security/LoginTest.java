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
    void navForUserHasNoAgentOrAdminLinks() throws Exception {
        mockMvc.perform(get("/").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Billy Test")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(not(containsString("href=\"/agent/tickets\""))));
    }

    @Test
    void navForAdminHasAdminAndAgentLinks() throws Exception {
        mockMvc.perform(get("/").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/users\"")))
                .andExpect(content().string(containsString("href=\"/agent/tickets\"")));
    }
}
