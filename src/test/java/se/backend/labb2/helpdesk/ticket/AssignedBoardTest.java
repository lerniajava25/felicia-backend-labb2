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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional: MockMvc runs on the test thread, so every test's data is rolled back.
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AssignedBoardTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;
    private User pippi;
    private User admin;
    private Ticket pippisWorking;
    private Ticket pippisWaiting;
    private Ticket adminsTicket;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        pippi = userRepository.save(new User("pippi", hash, "Pippi Test", Role.AGENT));
        User tommy = userRepository.save(new User("tommy", hash, "Tommy Test", Role.AGENT));
        admin = userRepository.save(new User("admin", hash, "Admin Test", Role.ADMIN));

        pippisWorking = ticket("Pippi working on it", pippi, TicketStatus.IN_PROGRESS);
        pippisWaiting = ticket("Pippi waiting for Billy", pippi, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER);
        ticket("Tommy ticket", tommy, TicketStatus.IN_PROGRESS);
        ticket("Unassigned ticket", null);
        adminsTicket = ticket("Admin ticket", admin, TicketStatus.IN_PROGRESS);
        ticketRepository.flush();
    }

    /** One of Billy's tickets, assigned to the given user (or nobody), moved through the steps. */
    private Ticket ticket(String title, User assignee, TicketStatus... steps) {
        Ticket ticket = new Ticket(title, "Details", billy);
        if (assignee != null) {
            ticket.assignTo(assignee);
        }
        for (TicketStatus step : steps) {
            ticket.changeStatus(step);
        }
        return ticketRepository.save(ticket);
    }

    /** GETs the board as the given user and returns it from the model. */
    private Board<AgentTicketRow> boardOf(User viewer) throws Exception {
        MvcResult result = mockMvc.perform(get("/agent/tickets/mine").with(user(CurrentUser.from(viewer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>" + AgentTicketController.ASSIGNED_HEADING + "</h1>")))
                .andReturn();
        return (Board<AgentTicketRow>) result.getModelAndView().getModel().get("board");
    }

    /** Ticket ids per column, so a test can compare the whole board at once. */
    private static Map<TicketStatus, List<Long>> idsByColumn(Board<AgentTicketRow> board) {
        return board.columns().stream().collect(Collectors.toMap(
                BoardColumn::status,
                column -> column.tickets().stream().map(AgentTicketRow::id).toList()));
    }

    @Test
    void agentSeesOnlyTicketsAssignedToThemInTheRightColumns() throws Exception {
        Board<AgentTicketRow> board = boardOf(pippi);

        // All five columns, in TicketStatus order, empty ones included.
        assertThat(board.columns()).extracting(BoardColumn::status)
                .containsExactly(TicketStatus.values());
        assertThat(idsByColumn(board)).isEqualTo(Map.of(
                TicketStatus.NEW, List.of(),
                TicketStatus.IN_PROGRESS, List.of(pippisWorking.getId()),
                TicketStatus.WAITING_FOR_USER, List.of(pippisWaiting.getId()),
                TicketStatus.RESOLVED, List.of(),
                TicketStatus.CLOSED, List.of()));

        mockMvc.perform(get("/agent/tickets/mine").with(user(CurrentUser.from(pippi))))
                .andExpect(content().string(allOf(
                        containsString("Pippi working on it"),
                        not(containsString("Tommy ticket")),
                        not(containsString("Unassigned ticket")),
                        not(containsString("Admin ticket")))));
    }

    @Test
    void adminSeesTheTicketAssignedToThem() throws Exception {
        Board<AgentTicketRow> board = boardOf(admin);

        assertThat(idsByColumn(board).get(TicketStatus.IN_PROGRESS)).containsExactly(adminsTicket.getId());
        assertThat(board.columns()).flatExtracting(BoardColumn::tickets).hasSize(1);
    }

    @Test
    void userIsForbidden() throws Exception {
        mockMvc.perform(get("/agent/tickets/mine").with(user(CurrentUser.from(billy))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void assignedBoardForAsUserThrows() {
        // No URL rule in front here: this checks @PreAuthorize on the method itself.
        assertThatThrownBy(() -> ticketService.assignedBoardFor(CurrentUser.from(billy)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }
}
