package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/** One column on the board: a status and the tickets that currently have it. */
public record BoardColumn(TicketStatus status, String label, List<TicketRow> tickets) {
}
