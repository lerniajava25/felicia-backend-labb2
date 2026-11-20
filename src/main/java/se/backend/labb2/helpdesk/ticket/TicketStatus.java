package se.backend.labb2.helpdesk.ticket;

import java.util.Locale;

public enum TicketStatus {
    NEW,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    CLOSED;

    /** Human-readable name for headings. The enum name is what's stored in the database. */
    public String label() {
        return switch (this) {
            case NEW -> "New";
            case IN_PROGRESS -> "In progress";
            case WAITING_FOR_USER -> "Waiting for user";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    /**
     * CSS class for the status colours in app.css: IN_PROGRESS becomes "status-in-progress".
     * Locale.ROOT so the result never depends on the server's language settings.
     */
    public String cssClass() {
        return "status-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean canMoveTo(TicketStatus next) {
        return switch (this) {
            case NEW -> next == IN_PROGRESS || next == CLOSED;
            case IN_PROGRESS -> next == WAITING_FOR_USER || next == RESOLVED;
            case WAITING_FOR_USER -> next == IN_PROGRESS || next == RESOLVED;
            case RESOLVED -> next == CLOSED || next == IN_PROGRESS;
            case CLOSED -> false;
        };
    }
}
