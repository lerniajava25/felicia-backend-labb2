package se.backend.labb2.helpdesk.ticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.security.CurrentUser;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /** The current user's own tickets, newest change first. */
    public List<TicketRow> listFor(CurrentUser current) {
        return ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(current.id()).stream()
                .map(TicketRow::from)
                .toList();
    }
}
