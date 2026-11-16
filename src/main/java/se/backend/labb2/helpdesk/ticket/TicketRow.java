package se.backend.labb2.helpdesk.ticket;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One line in a ticket list. A plain copy of the fields the template needs,
 * built inside the service transaction so no entity reaches the view.
 */
public record TicketRow(Long id, String title, TicketStatus status, String lastModified) {

    // Instant has no time zone, so the formatter needs one. Immutable, so safe to share.
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Stockholm"));

    public static TicketRow from(Ticket ticket) {
        return new TicketRow(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                FORMAT.format(ticket.getLastModified()));
    }
}
