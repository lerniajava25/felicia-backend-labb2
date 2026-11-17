package se.backend.labb2.helpdesk.ticket;

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
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AgentTicketListTest {

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
    private TicketService ticketService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;
    private User pippi;
    private User admin;
    private Ticket billysTicket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));

        billysTicket = new Ticket("Billy printer jam", "Paper everywhere", billy);
        billysTicket.assignTo(pippi);
        ticketRepository.saveAll(List.of(
                billysTicket,
                new Ticket("Olle needs a screen", "A big one", olle)));
        ticketRepository.flush();
    }

    @Test
    void agentSeesTicketsFromSeveralUsers() throws Exception {
        mockMvc.perform(get("/agent/tickets").with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Billy printer jam"),
                        containsString("Olle needs a screen"),
                        containsString("Billy Test"),
                        containsString("Olle Test"),
                        containsString("Pippi Test"),
                        containsString("Not assigned"))));
    }

    @Test
    void adminCanOpenAgentList() throws Exception {
        mockMvc.perform(get("/agent/tickets").with(user(CurrentUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Billy printer jam")));
    }

    @Test
    void userIsForbidden() throws Exception {
        mockMvc.perform(get("/agent/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/agent/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listAllAsUserThrows() {
        // No URL rule in front here: this checks @PreAuthorize on the method itself.
        assertThatThrownBy(() -> ticketService.listAll())
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void agentCanOpenAndCommentOnUsersTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Billy printer jam")));

        mockMvc.perform(post("/tickets/{id}/comments", billysTicket.getId())
                        .param("body", "On my way")
                        .with(user(CurrentUser.from(pippi)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + billysTicket.getId()));

        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(billysTicket.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().getAuthor().getId()).isEqualTo(pippi.getId());
    }

    @Test
    void navShowsAgentLinksForAgentAndAdminOnly() throws Exception {
        String allTickets = "href=\"/agent/tickets\"";
        String assigned = "href=\"/agent/tickets/mine\"";
        String myTickets = "href=\"/tickets\"";

        for (User agentOrAdmin : List.of(pippi, admin)) {
            mockMvc.perform(get("/tickets").with(user(CurrentUser.from(agentOrAdmin))))
                    .andExpect(content().string(allOf(containsString(allTickets), containsString(assigned))))
                    .andExpect(content().string(not(containsString(myTickets))));
        }
        mockMvc.perform(get("/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(content().string(containsString(myTickets)))
                .andExpect(content().string(not(containsString(allTickets))))
                .andExpect(content().string(not(containsString(assigned))));
    }
}
