package se.backend.labb2.helpdesk.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminUserListTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User pippi;
    private User billy;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        // Saved out of order, so the test shows that the list sorts by username.
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = new User("olle", hash, "Olle Test", Role.USER);
        olle.setEnabled(false);
        userRepository.save(olle);
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));
        userRepository.flush();
    }

    private String pageAs(User viewer) throws Exception {
        return mockMvc.perform(get("/admin/users").with(user(CurrentUser.from(viewer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void adminSeesAllUsersOrderedByUsername() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/users").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")   // the model holds Object; the controller puts a List<UserRow> there
        List<UserRow> rows = (List<UserRow>) result.getModelAndView().getModel().get("users");
        assertThat(rows).extracting(UserRow::username).containsExactly("admin", "billy", "olle", "pippi");

        String html = result.getResponse().getContentAsString();
        assertThat(html).containsSubsequence(">admin<", ">billy<", ">olle<", ">pippi<");
        assertThat(html).contains(
                "<h1>Users</h1>",
                "href=\"/admin/users/new\"",
                "href=\"/admin/users/" + billy.getId() + "/edit\"",
                "aria-label=\"Edit billy\"",
                ">Disabled<");
    }

    @Test
    void agentIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user(CurrentUser.from(pippi))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user(CurrentUser.from(billy))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void listAsAgentThrows() {
        // No URL rule in front here: this checks the @PreAuthorize on the service class.
        assertThatThrownBy(() -> userAdminService.list())
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void pageNeverShowsPasswordHashes() throws Exception {
        // Make sure there is something to leak: the stored hash really has the {bcrypt} prefix.
        String storedHash = userRepository.findById(billy.getId()).orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("{bcrypt}");

        String html = pageAs(admin);

        assertThat(html).doesNotContain("{bcrypt}", storedHash);
    }
}
