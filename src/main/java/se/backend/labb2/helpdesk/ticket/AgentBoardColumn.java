package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/** One column on an agents' board: a status and the board's tickets that currently have it. */
public record AgentBoardColumn(TicketStatus status, String label, List<AgentTicketRow> tickets) {
}
