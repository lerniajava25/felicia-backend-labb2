package se.backend.labb2.helpdesk.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HomePageTest {

    // The button markup, not just the words: the nav has links with the same labels.
    private static final String NEW_TICKET_BUTTON = "<a class=\"btn btn-primary\" href=\"/tickets/new\">New ticket</a>";
    private static final String USER_MY_TICKETS_BUTTON = "<a class=\"btn btn-secondary\" href=\"/tickets\">My tickets</a>";
    private static final String AGENT_MY_TICKETS_BUTTON = "<a class=\"btn btn-secondary\" href=\"/agent/tickets/mine\">My tickets</a>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User billy;
    private User pippi;
    private User admin;

    @BeforeEach
    void setUp() {
        // Real users: CurrentUserRefreshFilter logs out a user it can't find in the database.
        billy = userRepository.save(new User("billy", "unused-hash", "Billy Test", Role.USER));
        pippi = userRepository.save(new User("pippi", "unused-hash", "Pippi Test", Role.AGENT));
        admin = userRepository.save(new User("admin", "unused-hash", "Admin Test", Role.ADMIN));
    }

    private String homeAs(User viewer) throws Exception {
        return mockMvc.perform(get("/").with(user(CurrentUser.from(viewer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void userGetsNewTicketAndOwnTickets() throws Exception {
        String html = homeAs(billy);

        assertThat(html).contains(NEW_TICKET_BUTTON, USER_MY_TICKETS_BUTTON);
        assertThat(html).doesNotContain(AGENT_MY_TICKETS_BUTTON, "href=\"/agent/tickets/mine\"");
    }

    @Test
    void agentAndAdminGetNewTicketAndAssignedTickets() throws Exception {
        for (User agentOrAdmin : List.of(pippi, admin)) {
            String html = homeAs(agentOrAdmin);

            assertThat(html).contains(NEW_TICKET_BUTTON, AGENT_MY_TICKETS_BUTTON);
            assertThat(html).doesNotContain(USER_MY_TICKETS_BUTTON);
        }
    }

    @Test
    void eachMyTicketsButtonOpensAPageThatRoleMayUse() throws Exception {
        mockMvc.perform(get("/tickets/new").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/agent/tickets/mine").with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/agent/tickets/mine").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk());
    }
}
