package se.backend.labb2.helpdesk.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminCreateUserTest {

    private static final String TEST_PASSWORD = "made-up-test-password";
    private static final String NEW_PASSWORD = "twelve-chars";          // exactly 12: the lower bound
    private static final String LONG_PASSWORD = "a-long-and-valid-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User pippi;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        userRepository.flush();
    }

    /** A complete "New user" POST as the admin; tests change one field. */
    private MockHttpServletRequestBuilder createAs(User who, String username, String password) {
        return post("/admin/users")
                .param("username", username)
                .param("displayName", "New Person")
                .param("role", "AGENT")
                .param("password", password)
                .with(user(CurrentUser.from(who)))
                .with(csrf());
    }

    @Test
    void createdUserCanLogIn() throws Exception {
        assertThat(NEW_PASSWORD).hasSize(12);

        mockMvc.perform(createAs(admin, "newbie", NEW_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));

        mockMvc.perform(formLogin("/login").user("newbie").password(NEW_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated()
                        .withUsername("newbie")
                        .withAuthentication(auth -> assertThat(auth.getAuthorities())
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_AGENT")));
    }

    @Test
    void passwordIsHashedAndUsernameLowercased() throws Exception {
        mockMvc.perform(createAs(admin, "  BILLY2 ", LONG_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"));

        User created = userRepository.findByUsername("billy2").orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo(LONG_PASSWORD);
        assertThat(created.getDisplayName()).isEqualTo("New Person");
        assertThat(created.getRole()).isEqualTo(Role.AGENT);
        assertThat(userRepository.findByUsername("BILLY2")).isEmpty();
    }

    @Test
    void duplicateUsernameShowsFormErrorNotServerError() throws Exception {
        long before = userRepository.count();

        // "Billy" becomes "billy", which already exists.
        String html = mockMvc.perform(createAs(admin, "Billy", LONG_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/new-user"))
                .andExpect(model().attributeHasFieldErrorCode("form", "username", "taken"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(AdminUserController.USERNAME_TAKEN_MESSAGE);
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    void elevenCharacterPasswordIsRejected() throws Exception {
        String eleven = "eleven-char";
        assertThat(eleven).hasSize(11);

        mockMvc.perform(createAs(admin, "shorty", eleven))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/new-user"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        assertThat(userRepository.findByUsername("shorty")).isEmpty();
    }

    @Test
    void validationErrorNeverSendsThePasswordBack() throws Exception {
        // An error elsewhere in the form, with a valid password that must not be echoed.
        String badUsername = mockMvc.perform(createAs(admin, "no spaces!", LONG_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "username"))
                .andReturn().getResponse().getContentAsString();
        assertThat(badUsername).doesNotContain(LONG_PASSWORD);
        // The other typed values do come back.
        assertThat(badUsername).contains("value=\"New Person\"");

        // An error on the password itself.
        String shortPassword = mockMvc.perform(createAs(admin, "shorty", "eleven-char"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(shortPassword).doesNotContain("eleven-char");
    }

    @Test
    void agentIsForbidden() throws Exception {
        mockMvc.perform(createAs(pippi, "sneaky", LONG_PASSWORD))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByUsername("sneaky")).isEmpty();
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .param("username", "nocsrf")
                        .param("displayName", "New Person")
                        .param("role", "USER")
                        .param("password", LONG_PASSWORD)
                        .with(user(CurrentUser.from(admin))))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByUsername("nocsrf")).isEmpty();
    }

    @Test
    void formHasPasswordFieldForNewPasswords() throws Exception {
        String html = mockMvc.perform(get("/admin/users/new").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(
                "type=\"password\"",
                "autocomplete=\"new-password\"",
                "action=\"/admin/users\"",
                "name=\"_csrf\"");
    }
}
