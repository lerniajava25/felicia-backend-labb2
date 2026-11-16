package se.backend.labb2.helpdesk.ticket;

import se.backend.labb2.helpdesk.common.DateTimes;

/**
 * One line in a ticket list. A plain copy of the fields the template needs,
 * built inside the service transaction so no entity reaches the view.
 */
public record TicketRow(Long id, String title, TicketStatus status, String lastModified) {

    public static TicketRow from(Ticket ticket) {
        return new TicketRow(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                DateTimes.format(ticket.getLastModified()));
    }
}
