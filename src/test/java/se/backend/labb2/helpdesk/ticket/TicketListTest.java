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
import org.springframework.util.StringUtils;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketListTest {

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
    private TicketService ticketService;

    private User billy;
    private User nobody;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        nobody = userRepository.save(new User("nobody", hash, "No Tickets", Role.USER));

        Ticket inProgress = new Ticket("Billy forgot password", "Description", billy);
        inProgress.changeStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.saveAll(List.of(
                new Ticket("Billy printer jam", "Description", billy),
                inProgress,
                new Ticket("Olle needs a screen", "Description", olle)));
        ticketRepository.flush();
    }

    @Test
    void boardPutsTicketsInTheirStatusColumn() {
        Board board = ticketService.boardFor(CurrentUser.from(billy));

        assertThat(board.empty()).isFalse();
        assertThat(board.columns())
                .extracting(BoardColumn::status)
                .containsExactly(TicketStatus.values());
        assertThat(titlesIn(board, TicketStatus.NEW)).containsExactly("Billy printer jam");
        assertThat(titlesIn(board, TicketStatus.IN_PROGRESS)).containsExactly("Billy forgot password");
        assertThat(titlesIn(board, TicketStatus.WAITING_FOR_USER)).isEmpty();
        assertThat(titlesIn(board, TicketStatus.RESOLVED)).isEmpty();
        assertThat(titlesIn(board, TicketStatus.CLOSED)).isEmpty();
    }

    @Test
    void boardShowsAllColumnHeadings() throws Exception {
        mockMvc.perform(get("/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString(">New<"),
                        containsString(">In progress<"),
                        containsString(">Waiting for user<"),
                        containsString(">Resolved<"),
                        containsString(">Closed<"))));
    }

    @Test
    void userBoardHasNoWayToMoveTickets() throws Exception {
        String html = mockMvc.perform(get("/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The only form and button on the page are the nav's logout.
        assertThat(StringUtils.countOccurrencesOf(html, "<form")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<button")).isEqualTo(1);
        assertThat(html).contains("action=\"/logout\"");
    }

    private static List<String> titlesIn(Board board, TicketStatus status) {
        return board.columns().stream()
                .filter(column -> column.status() == status)
                .findFirst().orElseThrow()
                .tickets().stream()
                .map(TicketRow::title)
                .toList();
    }

    @Test
    void userSeesOnlyOwnTickets() throws Exception {
        mockMvc.perform(get("/tickets").with(user(CurrentUser.from(billy))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Billy printer jam"),
                        containsString("Billy forgot password"))))
                .andExpect(content().string(not(containsString("Olle needs a screen"))));
    }

    @Test
    void emptyListShowsFriendlyMessage() throws Exception {
        mockMvc.perform(get("/tickets").with(user(CurrentUser.from(nobody))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have no tickets yet.")));
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
