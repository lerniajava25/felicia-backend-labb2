package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.backend.labb2.helpdesk.common.RuleViolationException;
import se.backend.labb2.helpdesk.security.CurrentUser;

/** Agent pages. Access is checked by the /agent/** URL rule and @PreAuthorize in the service. */
@Controller
public class AgentTicketController {

    static final String CONFLICT_MESSAGE = "Someone else changed this ticket. Check it and try again.";
    static final String ASSIGNED_MESSAGE = "The ticket is now assigned to you.";
    static final String STATUS_CHANGED_MESSAGE = "The status has been changed.";

    private final TicketService ticketService;

    public AgentTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/agent/tickets")
    public String list(Model model) {
        model.addAttribute("board", ticketService.boardOfAll());
        return "agent/tickets";
    }

    @PostMapping("/agent/tickets/{id}/assign")
    public String assignToMe(@PathVariable Long id,
                             @Valid AssignForm form,
                             BindingResult result,
                             @AuthenticationPrincipal CurrentUser current,
                             RedirectAttributes flash) {
        if (result.hasErrors()) {
            // The button always sends the version; only a hand-made request lacks it.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            ticketService.assignToMe(id, form.getVersion(), current);
            flash.addFlashAttribute("message", ASSIGNED_MESSAGE);
        } catch (OptimisticLockingFailureException e) {
            // Covers both the version check in the service and Hibernate's check at commit.
            flash.addFlashAttribute("error", CONFLICT_MESSAGE);
        } catch (RuleViolationException e) {
            // A business rule said no; its message is written for the user.
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/agent/tickets/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @Valid ChangeStatusForm form,
                               BindingResult result,
                               @AuthenticationPrincipal CurrentUser current,
                               RedirectAttributes flash) {
        if (result.hasErrors()) {
            // The dropdown always sends a real status and the version; only a hand-made request doesn't.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            ticketService.changeStatus(id, form.getStatus(), form.getReason(), form.getVersion(), current);
            flash.addFlashAttribute("message", STATUS_CHANGED_MESSAGE);
        } catch (OptimisticLockingFailureException e) {
            // Covers both the version check in the service and Hibernate's check at commit.
            flash.addFlashAttribute("error", CONFLICT_MESSAGE);
        } catch (RuleViolationException e) {
            // Move not allowed, or a reason is missing; the service's message says which.
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tickets/" + id;
    }
}
