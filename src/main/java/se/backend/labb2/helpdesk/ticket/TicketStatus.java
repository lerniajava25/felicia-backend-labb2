package se.backend.labb2.helpdesk.ticket;

public enum TicketStatus {
    NEW,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    CLOSED;

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
