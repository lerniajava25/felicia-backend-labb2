package se.backend.labb2.helpdesk.ticket;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Agent pages. Access is checked by the /agent/** URL rule and @PreAuthorize in the service. */
@Controller
public class AgentTicketController {

    private final TicketService ticketService;

    public AgentTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/agent/tickets")
    public String list(Model model) {
        model.addAttribute("board", ticketService.boardOfAll());
        return "agent/tickets";
    }
}
