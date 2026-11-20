package se.backend.labb2.helpdesk.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.common.RuleViolationException;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.ticket.Ticket;
import se.backend.labb2.helpdesk.ticket.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminEditUserTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserAdminService userAdminService;

    private User admin;
    private User boss;
    private User pippi;
    private User olle;
    private User billy;
    private Ticket billysTicket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));
        boss = userRepository.save(new User("boss", hash, "Boss Test", Role.ADMIN));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        billysTicket = ticketRepository.save(new Ticket("Billy printer jam", "Paper everywhere", billy));
        userRepository.flush();
    }

    /** Writes pending changes and reads the entity back from the database. */
    private <T> T reload(T entity) {
        entityManager.flush();
        entityManager.refresh(entity);
        return entity;
    }

    /**
     * The edit form as a browser sends it. An unticked checkbox sends only the "_enabled"
     * marker, which binds to false, so "enabled" is left out to disable.
     */
    private MockHttpServletRequestBuilder save(User who, User target, Role role, boolean enabled, Long version) {
        MockHttpServletRequestBuilder request = post("/admin/users/{id}", target.getId())
                .param("role", role.name())
                .param("_enabled", "on")
                .param("version", version.toString())
                .with(user(CurrentUser.from(who)))
                .with(csrf());
        if (enabled) {
            request.param("enabled", "true");
        }
        return request;
    }

    /** Logs in with the real login form and returns the session it created. */
    private MockHttpSession logIn(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(TEST_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void olleBecomesAgentAndReachesAgentPagesAfterNewLogin() throws Exception {
        // Before: a user is kept out, so the test really shows the change.
        mockMvc.perform(get("/agent/tickets").session(logIn("olle")))
                .andExpect(status().isForbidden());

        mockMvc.perform(save(admin, olle, Role.AGENT, true, olle.getVersion()))
                .andExpect(redirectedUrl("/admin/users"));
        userRepository.flush();

        mockMvc.perform(get("/agent/tickets").session(logIn("olle")))
                .andExpect(status().isOk());
    }

    @Test
    void disabledUserCannotLogInButOldTicketsKeepTheirName() throws Exception {
        mockMvc.perform(save(admin, billy, Role.USER, false, billy.getVersion()))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(reload(billy).getEnabled()).isFalse();

        mockMvc.perform(formLogin("/login").user("billy").password(TEST_PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        // Nothing is deleted: billy's ticket still shows who created it.
        String html = mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Billy printer jam", "Billy Test");
    }

    @Test
    void adminCannotDisableOrDemoteThemselves() throws Exception {
        String editPage = "/admin/users/" + admin.getId() + "/edit";

        mockMvc.perform(save(admin, admin, Role.ADMIN, false, admin.getVersion()))
                .andExpect(redirectedUrl(editPage))
                .andExpect(flash().attribute("error", UserAdminService.OWN_ACCOUNT_MESSAGE));
        mockMvc.perform(save(admin, admin, Role.AGENT, true, admin.getVersion()))
                .andExpect(redirectedUrl(editPage))
                .andExpect(flash().attribute("error", UserAdminService.OWN_ACCOUNT_MESSAGE));

        User stored = reload(admin);
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored.getEnabled()).isTrue();
    }

    @Test
    void oldVersionIsRefused() throws Exception {
        // Another admin changes olle after "our" page was loaded.
        Long versionOnOurPage = olle.getVersion();
        olle.changeRole(Role.AGENT);
        userRepository.flush();
        assertThat(olle.getVersion()).isGreaterThan(versionOnOurPage);

        mockMvc.perform(save(admin, olle, Role.ADMIN, true, versionOnOurPage))
                .andExpect(redirectedUrl("/admin/users/" + olle.getId() + "/edit"))
                .andExpect(flash().attribute("error", AdminUserController.CONFLICT_MESSAGE));

        assertThat(reload(olle).getRole()).isEqualTo(Role.AGENT);   // the other admin's change, not ours
    }

    @Test
    void agentIsForbiddenOnGetAndPost() throws Exception {
        mockMvc.perform(get("/admin/users/{id}/edit", olle.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(status().isForbidden());
        mockMvc.perform(save(pippi, olle, Role.ADMIN, true, olle.getVersion()))
                .andExpect(status().isForbidden());

        assertThat(reload(olle).getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(roles = "ADMIN")   // passes the service's @PreAuthorize
    void lastEnabledAdminCannotBeDemoted() {
        // The acting admin's own account was disabled meanwhile, so boss is the only enabled
        // admin left. Over HTTP that admin is now logged out first (CurrentUserRefreshFilter),
        // so the service is called directly: the rule stays as a second line of defence.
        admin.setEnabled(false);
        userRepository.flush();
        CurrentUser staleAdmin = CurrentUser.from(admin);

        assertThatThrownBy(() -> userAdminService.update(boss.getId(), form(Role.USER, true, boss.getVersion()), staleAdmin))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage(UserAdminService.LAST_ADMIN_MESSAGE);
        assertThat(reload(boss).getRole()).isEqualTo(Role.ADMIN);
        assertThat(reload(boss).getEnabled()).isTrue();

        // With a second enabled admin the same change is fine.
        admin.setEnabled(true);
        userRepository.flush();
        userAdminService.update(boss.getId(), form(Role.USER, true, boss.getVersion()), CurrentUser.from(admin));
        assertThat(reload(boss).getRole()).isEqualTo(Role.USER);
    }

    private static EditUserForm form(Role role, boolean enabled, Long version) {
        EditUserForm form = new EditUserForm();
        form.setRole(role);
        form.setEnabled(enabled);
        form.setVersion(version);
        return form;
    }

    @Test
    void postWithoutCsrfIsForbiddenAndUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(post("/admin/users/{id}", olle.getId())
                        .param("role", "ADMIN")
                        .param("_enabled", "on")
                        .param("enabled", "true")
                        .param("version", olle.getVersion().toString())
                        .with(user(CurrentUser.from(admin))))
                .andExpect(status().isForbidden());
        assertThat(reload(olle).getRole()).isEqualTo(Role.USER);

        mockMvc.perform(get("/admin/users/{id}/edit", 999_999L).with(user(CurrentUser.from(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void editPageShowsNamesAsTextAndHasNoPasswordField() throws Exception {
        String html = mockMvc.perform(get("/admin/users/{id}/edit", olle.getId()).with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(
                "<dd>olle</dd>",
                "<dd>Olle Test</dd>",
                "type=\"hidden\" id=\"version\" name=\"version\" value=\"" + olle.getVersion() + "\"",
                "type=\"checkbox\"",
                "name=\"_enabled\"");
        assertThat(html).doesNotContain("type=\"password\"", "name=\"username\"", "name=\"displayName\"");
    }
}
