package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    // The fixed path "/tickets/new" wins over "/tickets/{id}", so "new" is never read as an id.
    @GetMapping("/tickets/new")
    public String newForm(Model model) {
        model.addAttribute("form", new NewTicketForm());
        return "tickets/new";
    }

    @PostMapping("/tickets")
    public String create(@Valid @ModelAttribute("form") NewTicketForm form,
                         BindingResult result,
                         @AuthenticationPrincipal CurrentUser current) {
        if (result.hasErrors()) {
            // Same page again: the form (with the typed text) and the errors are still in the model.
            return "tickets/new";
        }
        Long id = ticketService.create(form, current);
        return "redirect:/tickets/" + id;
    }

    @GetMapping("/tickets/{id}")
    public String details(@PathVariable Long id, @AuthenticationPrincipal CurrentUser current, Model model) {
        model.addAttribute("ticket", ticketService.details(id, current));
        model.addAttribute("commentForm", new CommentForm());
        return "tickets/detail";
    }

    @PostMapping("/tickets/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @Valid @ModelAttribute("commentForm") CommentForm form,
                             BindingResult result,
                             @AuthenticationPrincipal CurrentUser current,
                             Model model) {
        if (result.hasErrors()) {
            // details() goes through loadVisible too, so someone else's ticket is still 404.
            model.addAttribute("ticket", ticketService.details(id, current));
            return "tickets/detail";
        }
        ticketService.addComment(id, form, current);
        return "redirect:/tickets/" + id;
    }
}
