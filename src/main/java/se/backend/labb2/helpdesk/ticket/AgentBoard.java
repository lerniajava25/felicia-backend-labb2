package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/**
 * The agents' board of all tickets: one column per status, in TicketStatus order.
 *
 * @param empty true when there are no tickets at all, so the template needs no logic
 */
public record AgentBoard(List<AgentBoardColumn> columns, boolean empty) {
}
