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
import static org.hamcrest.Matchers.not;
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
class TicketCommentTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;
    private User pippi;
    private Ticket billysTicket;
    private Ticket ollesTicket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));

        billysTicket = ticketRepository.save(new Ticket("Billy printer jam", "Paper everywhere", billy));
        ollesTicket = ticketRepository.save(new Ticket("Olle needs a screen", "A big one", olle));
        ticketRepository.flush();
    }

    @Test
    void userCommentsOnOwnTicket() throws Exception {
        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", "Still broken")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + billysTicket.getId()));

        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(billysTicket.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().getBody()).isEqualTo("Still broken");
        assertThat(comments.getFirst().getAuthor().getId()).isEqualTo(billy.getId());

        mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Still broken"),
                        containsString("Billy Test"))));
    }

    @Test
    void commentOnOthersTicketIsNotFoundAndNotSaved() throws Exception {
        long before = commentRepository.count();

        mockMvc.perform(post("/tickets/{id}/comments", ollesTicket.getId())
                        .param("body", "Let me in")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    void emptyCommentShowsErrorAndSavesNothing() throws Exception {
        long before = commentRepository.count();

        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", "")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("tickets/detail"))
                .andExpect(model().attributeHasFieldErrors("commentForm", "body"))
                .andExpect(content().string(allOf(
                        containsString("Comment cannot be empty"),
                        containsString("Billy printer jam"))));

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    @Test
    void scriptInCommentIsEscaped() throws Exception {
        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", "<script>alert(1)</script>")
                        .with(user(CurrentUser.from(billy)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        long before = commentRepository.count();

        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", "Still broken")
                        .with(user(CurrentUser.from(billy))))
                .andExpect(status().isForbidden());

        assertThat(commentRepository.count()).isEqualTo(before);
    }

    /** Moves Billy's ticket step by step, as an agent did earlier. */
    private void moveBillysTicketTo(TicketStatus... steps) {
        for (TicketStatus step : steps) {
            billysTicket.changeStatus(step);
        }
        ticketRepository.flush();
    }

    private void comment(User author, String body) throws Exception {
        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", body)
                        .with(user(CurrentUser.from(author)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + billysTicket.getId()));
    }

    @Test
    void ownerAnswerMovesWaitingTicketBackToInProgress() throws Exception {
        moveBillysTicketTo(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER);

        comment(billy, "Here is the printer model");

        assertThat(billysTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void agentCommentKeepsTicketWaiting() throws Exception {
        moveBillysTicketTo(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER);

        comment(pippi, "Which printer model is it?");

        assertThat(billysTicket.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_USER);
        assertThat(commentRepository.findByTicketIdOrderByCreatedAtAsc(billysTicket.getId())).hasSize(1);
    }

    @Test
    void ownerCommentOnInProgressTicketKeepsStatus() throws Exception {
        moveBillysTicketTo(TicketStatus.IN_PROGRESS);

        comment(billy, "Any news?");

        assertThat(billysTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }
}
