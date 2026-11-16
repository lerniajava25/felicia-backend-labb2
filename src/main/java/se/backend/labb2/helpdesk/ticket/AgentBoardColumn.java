package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/** One column on the agents' board: a status and every ticket that currently has it. */
public record AgentBoardColumn(TicketStatus status, String label, List<AgentTicketRow> tickets) {
}
