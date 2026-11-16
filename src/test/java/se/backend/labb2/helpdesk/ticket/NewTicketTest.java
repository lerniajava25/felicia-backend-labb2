package se.backend.labb2.helpdesk.ticket;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NewTicketTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;
    private User olle;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        userRepository.flush();
    }

    @Test
    void newTicketPageShowsForm() throws Exception {
        mockMvc.perform(get("/tickets/new").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("action=\"/tickets\""),
                        containsString("name=\"_csrf\""),
                        containsString("name=\"title\""),
                        containsString("name=\"description\""))));
    }

    @Test
    void validPostCreatesNewTicketOwnedByUser() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("title", "  Printer jam  ")
                        .param("description", "Paper everywhere")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<Ticket> tickets = ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(billy.getId());
        assertThat(tickets).hasSize(1);
        Ticket created = tickets.getFirst();
        assertThat(created.getTitle()).isEqualTo("Printer jam");
        assertThat(created.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(created.getAssignee()).isNull();
    }

    @Test
    void validPostRedirectsToTheNewTicket() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("title", "Printer jam")
                        .param("description", "Paper everywhere")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + onlyTicketOf(billy).getId()));
    }

    @Test
    void emptyTitleShowsFormAgainAndSavesNothing() throws Exception {
        long before = ticketRepository.count();

        mockMvc.perform(post("/tickets")
                        .param("title", "")
                        .param("description", "Typed text")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("tickets/new"))
                .andExpect(model().attributeHasFieldErrors("form", "title"))
                .andExpect(content().string(allOf(
                        containsString("Title is required"),
                        containsString("Typed text"))));

        assertThat(ticketRepository.count()).isEqualTo(before);
    }

    @Test
    void titleOf121CharactersIsRejected() throws Exception {
        long before = ticketRepository.count();

        mockMvc.perform(post("/tickets")
                        .param("title", "a".repeat(121))
                        .param("description", "Description")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("tickets/new"))
                .andExpect(model().attributeHasFieldErrorCode("form", "title", "Size"));

        assertThat(ticketRepository.count()).isEqualTo(before);
    }

    @Test
    void extraParametersAreIgnored() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("title", "Printer jam")
                        .param("description", "Paper everywhere")
                        // Not in NewTicketForm, so there is nothing for them to bind to.
                        .param("status", "CLOSED")
                        .param("createdBy", olle.getId().toString())
                        .param("assignee", olle.getId().toString())
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Ticket created = onlyTicketOf(billy);
        assertThat(created.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(created.getAssignee()).isNull();
        assertThat(ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(olle.getId())).isEmpty();
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        long before = ticketRepository.count();

        mockMvc.perform(post("/tickets")
                        .param("title", "Printer jam")
                        .param("description", "Paper everywhere")
                        .with(user(CurrentUser.from(billy))))
                .andExpect(status().isForbidden());

        assertThat(ticketRepository.count()).isEqualTo(before);
    }

    private Ticket onlyTicketOf(User owner) {
        List<Ticket> tickets = ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(owner.getId());
        assertThat(tickets).hasSize(1);
        return tickets.getFirst();
    }
}
