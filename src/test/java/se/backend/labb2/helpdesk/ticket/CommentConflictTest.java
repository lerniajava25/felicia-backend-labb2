package se.backend.labb2.helpdesk.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * A real edit conflict needs two committed transactions at the same time, which a
 * @Transactional MockMvc test can't produce. So the service is mocked to throw what
 * Spring throws at commit, and this checks what the owner gets back.
 */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentConflictTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void conflictKeepsTheReplyAndExplains() throws Exception {
        CurrentUser owner = new CurrentUser(1L, "billy", "unused-hash", "Billy Test", Role.USER, true);
        doThrow(new ObjectOptimisticLockingFailureException(Ticket.class, 7L))
                .when(ticketService).addComment(eq(7L), any(CommentForm.class), any(CurrentUser.class));

        mockMvc.perform(post("/tickets/{id}/comments", 7L)
                        .param("body", "Here is the printer model")
                        .with(user(owner))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tickets/7"))
                .andExpect(flash().attribute("error", TicketController.COMMENT_CONFLICT_MESSAGE))
                .andExpect(flash().attribute("commentForm", hasProperty("body", is("Here is the printer model"))));
    }
}
