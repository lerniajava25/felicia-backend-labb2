package se.backend.labb2.helpdesk.ticket;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.backend.labb2.helpdesk.security.CurrentUser;

@Controller
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tickets")
    public String list(@AuthenticationPrincipal CurrentUser current, Model model) {
        model.addAttribute("board", ticketService.boardFor(current));
        return "tickets/list";
    }

    @GetMapping("/tickets/{id}")
    public String details(@PathVariable Long id, @AuthenticationPrincipal CurrentUser current, Model model) {
        model.addAttribute("ticket", ticketService.details(id, current));
        return "tickets/detail";
    }
}
