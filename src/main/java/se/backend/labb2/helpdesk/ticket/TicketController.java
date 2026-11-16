package se.backend.labb2.helpdesk.ticket;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import se.backend.labb2.helpdesk.security.CurrentUser;

@Controller
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tickets")
    public String list(@AuthenticationPrincipal CurrentUser current, Model model) {
        model.addAttribute("tickets", ticketService.listFor(current));
        return "tickets/list";
    }
}
