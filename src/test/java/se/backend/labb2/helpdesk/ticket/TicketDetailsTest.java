package se.backend.labb2.helpdesk.ticket;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketDetailsTest {

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
        commentRepository.save(new Comment(billysTicket, pippi, "Have you tried turning it off?"));
        commentRepository.flush();
    }

    @Test
    void userSeesOwnTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Billy printer jam"),
                        // The comment and its lazy author were read inside the transaction.
                        containsString("Have you tried turning it off?"),
                        containsString("Pippi Test"))));
    }

    @Test
    void userGetsNotFoundForOthersTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ollesTicket.getId()).with(user(CurrentUser.from(billy))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTicketLooksTheSameAsOthersTicket() throws Exception {
        Long missingId = ollesTicket.getId() + 1000;

        MockHttpServletResponse others = mockMvc
                .perform(get("/tickets/{id}", ollesTicket.getId()).with(user(CurrentUser.from(billy))))
                .andReturn().getResponse();
        MockHttpServletResponse missing = mockMvc
                .perform(get("/tickets/{id}", missingId).with(user(CurrentUser.from(billy))))
                .andReturn().getResponse();

        // MockMvc doesn't forward to /error, so this compares what our code sends:
        // status, error message and body. The real error page is chosen by status alone.
        assertThat(others.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(others.getStatus());
        assertThat(missing.getErrorMessage()).isEqualTo(others.getErrorMessage());
        assertThat(missing.getContentAsString()).isEqualTo(others.getContentAsString());
    }

    @Test
    void agentSeesAnyTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", billysTicket.getId()).with(user(CurrentUser.from(pippi))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Billy printer jam")));
    }

    @Test
    void notFoundPageShowsNavForLoggedInUser() throws Exception {
        // Call Boot's error controller the way the server's forward would.
        // Accept text/html like a browser; without it Boot answers with JSON.
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .with(user(CurrentUser.from(billy))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(allOf(
                        containsString("Page not found"),
                        containsString("Billy Test"),
                        containsString("href=\"/tickets\""))));
    }
}
