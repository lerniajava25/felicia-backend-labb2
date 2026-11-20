package se.backend.labb2.helpdesk.ticket;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AssignTicketTest {

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

    private User billy;
    private User pippi;
    private User tommy;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        tommy = userRepository.save(new User("tommy", hash, "Tommy Test", Role.AGENT));
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));

        ticket = ticketRepository.saveAndFlush(new Ticket("Billy printer jam", "Paper everywhere", billy));
    }

    /** Writes pending changes and reads the entity back from the database. */
    private <T> T reload(T entity) {
        entityManager.flush();
        entityManager.refresh(entity);
        return entity;
    }

    @Test
    void agentAssignsNewTicket() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("message", AgentTicketController.ASSIGNED_MESSAGE));

        assertThat(reload(ticket).getAssignee().getId()).isEqualTo(pippi.getId());
        assertThat(reload(ticket).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void adminCanAssignToo() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(admin)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()));

        assertThat(reload(ticket).getAssignee().getId()).isEqualTo(admin.getId());
    }

    @Test
    void oldVersionChangesNothingAndShowsMessage() throws Exception {
        // Someone else changes the ticket after "our" page was loaded: version 0 -> 1.
        Long versionOnOurPage = ticket.getVersion();
        ticket.changeStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.flush();
        assertThat(ticket.getVersion()).isGreaterThan(versionOnOurPage);

        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", versionOnOurPage.toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("error", AgentTicketController.CONFLICT_MESSAGE));

        assertThat(reload(ticket).getAssignee()).isNull();
        assertThat(reload(ticket).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        // The page after the redirect shows the message.
        mockMvc.perform(get("/tickets/{id}", ticket.getId())
                        .flashAttr("error", AgentTicketController.CONFLICT_MESSAGE)
                        .with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(AgentTicketController.CONFLICT_MESSAGE)));
    }

    @Test
    void userGetsForbiddenAndNothingChanges() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(reload(ticket).getAssignee()).isNull();
        assertThat(reload(ticket).getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(pippi))))
                .andExpect(status().isForbidden());

        assertThat(reload(ticket).getAssignee()).isNull();
        assertThat(reload(ticket).getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void userDetailsPageHasNoAssignButton() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Assign to me"))));

        // Make sure the test would see the button if it were there.
        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(content().string(containsString("Assign to me")));
    }

    // The button markup, not just the words: HTML comments in the template reach the page too.
    private static final String ASSIGN_BUTTON = ">Assign to me</button>";
    private static final String TAKE_OVER_BUTTON = ">Take over</button>";

    /** The ticket as an agent sees it once pippi has it. */
    private String pageAfterPippiTookIt(User viewer) throws Exception {
        ticket.assignTo(pippi);
        ticket.changeStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.flush();
        return mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(user(CurrentUser.from(viewer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void assigneeSeesNoAssignButton() throws Exception {
        String html = pageAfterPippiTookIt(pippi);

        assertThat(html).doesNotContain(ASSIGN_BUTTON, TAKE_OVER_BUTTON);
        assertThat(html).contains("Change status");   // the agent tools are still there
    }

    @Test
    void otherAgentSeesTakeOver() throws Exception {
        String html = pageAfterPippiTookIt(tommy);

        assertThat(html).contains(TAKE_OVER_BUTTON);
        assertThat(html).doesNotContain(ASSIGN_BUTTON);
    }

    @Test
    void takeOverStillWorksOnTheServer() throws Exception {
        ticket.assignTo(pippi);
        ticketRepository.flush();

        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(tommy)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("message", AgentTicketController.ASSIGNED_MESSAGE));

        assertThat(reload(ticket).getAssignee().getId()).isEqualTo(tommy.getId());
    }

    @Test
    void closedTicketIsRefused() throws Exception {
        ticket.changeStatus(TicketStatus.CLOSED);
        ticketRepository.flush();

        mockMvc.perform(post("/agent/tickets/{id}/assign", ticket.getId())
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("error", "A closed ticket cannot be assigned."));

        assertThat(reload(ticket).getAssignee()).isNull();
        assertThat(reload(ticket).getStatus()).isEqualTo(TicketStatus.CLOSED);
    }
}
