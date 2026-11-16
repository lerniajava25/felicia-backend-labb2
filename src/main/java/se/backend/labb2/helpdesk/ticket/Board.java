package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/**
 * The ticket board: one column per status, in TicketStatus order.
 *
 * @param empty true when no column has any tickets, so the template needs no logic
 */
public record Board(List<BoardColumn> columns, boolean empty) {
}
