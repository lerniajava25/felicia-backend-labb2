package se.backend.labb2.helpdesk.ticket;

import java.util.List;

/** One column on a board: a status and the board's tickets that currently have it. */
public record BoardColumn<R>(TicketStatus status, String label, List<R> tickets) {
}
