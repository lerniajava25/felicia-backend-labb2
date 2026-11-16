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

    private User billy;
    private User nobody;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        billy = userRepository.save(new User("billy", hash, "Billy Test", Role.USER));
        User olle = userRepository.save(new User("olle", hash, "Olle Test", Role.USER));
        nobody = userRepository.save(new User("nobody", hash, "No Tickets", Role.USER));

        ticketRepository.saveAll(List.of(
                new Ticket("Billy printer jam", "Description", billy),
                new Ticket("Billy forgot password", "Description", billy),
                new Ticket("Olle needs a screen", "Description", olle)));
        ticketRepository.flush();
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
