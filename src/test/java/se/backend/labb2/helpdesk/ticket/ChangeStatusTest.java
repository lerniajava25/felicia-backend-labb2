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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
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
class ChangeStatusTest {

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
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));

        ticket = ticketRepository.saveAndFlush(new Ticket("Billy printer jam", "Paper everywhere", billy));
    }

    /** Moves the test ticket in the database, as if someone did it earlier. */
    private void moveTo(TicketStatus status) {
        ticket.changeStatus(status);
        ticketRepository.flush();
    }

    @Test
    void inProgressToResolvedWorksAndIsSaved() throws Exception {
        moveTo(TicketStatus.IN_PROGRESS);

        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "RESOLVED")
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("message", AgentTicketController.STATUS_CHANGED_MESSAGE));

        // Write to the database, forget the cached entity, and read it back from the database.
        entityManager.flush();
        entityManager.clear();
        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void closedToNewIsRefusedAndNothingChanges() throws Exception {
        moveTo(TicketStatus.CLOSED);
        Long versionBefore = ticket.getVersion();

        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "NEW")
                        .param("version", versionBefore.toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("error", AgentTicketController.STATUS_NOT_ALLOWED_MESSAGE));

        ticketRepository.flush();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getVersion()).isEqualTo(versionBefore);
    }

    @Test
    void oldVersionChangesNothingAndShowsMessage() throws Exception {
        // Someone else changes the ticket after "our" page was loaded: version 0 -> 1.
        Long versionOnOurPage = ticket.getVersion();
        moveTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getVersion()).isGreaterThan(versionOnOurPage);

        // RESOLVED is allowed from IN_PROGRESS, so only the version can stop it.
        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "RESOLVED")
                        .param("version", versionOnOurPage.toString())
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("error", AgentTicketController.CONFLICT_MESSAGE));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        // The page after the redirect shows the message.
        mockMvc.perform(get("/tickets/{id}", ticket.getId())
                        .flashAttr("error", AgentTicketController.CONFLICT_MESSAGE)
                        .with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(AgentTicketController.CONFLICT_MESSAGE)));
    }

    @Test
    void userGetsForbiddenAndNothingChanges() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "CLOSED")
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void adminCanChangeStatusToo() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "CLOSED")
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(admin)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket.getId()))
                .andExpect(flash().attribute("message", AgentTicketController.STATUS_CHANGED_MESSAGE));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/agent/tickets/{id}/status", ticket.getId())
                        .param("status", "CLOSED")
                        .param("version", ticket.getVersion().toString())
                        .with(user(CurrentUser.from(pippi))))
                .andExpect(status().isForbidden());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void dropdownForNewTicketListsExactlyItsAllowedMoves() throws Exception {
        MvcResult result = mockMvc.perform(get("/tickets/{id}", ticket.getId())
                        .with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<option value=\"IN_PROGRESS\">In progress</option>"),
                        containsString("<option value=\"CLOSED\">Closed</option>"),
                        not(containsString("<option value=\"NEW\"")),
                        not(containsString("<option value=\"WAITING_FOR_USER\"")),
                        not(containsString("<option value=\"RESOLVED\"")))))
                .andReturn();

        // Exactly these two, in this order: nothing extra hides in the list.
        TicketDetails details = (TicketDetails) result.getModelAndView().getModel().get("ticket");
        assertThat(details.allowedMoves()).containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED);
    }

    @Test
    void closedTicketHasNoStatusForm() throws Exception {
        moveTo(TicketStatus.CLOSED);

        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Change status"))));

        // Make sure the test would see the form if it were there.
        Ticket open = ticketRepository.saveAndFlush(new Ticket("Open one", "Still open", billy));
        mockMvc.perform(get("/tickets/{id}", open.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(content().string(containsString("Change status")));
    }

    @Test
    void userDetailsPageHasNoStatusForm() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Change status"))));
    }
}
