package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/**
 * An agents' board ("All tickets" or "Assigned to me"): one column per status, in
 * TicketStatus order.
 *
 * @param empty true when the board has no tickets at all, so the template needs no logic
 */
public record AgentBoard(List<AgentBoardColumn> columns, boolean empty) {
}
