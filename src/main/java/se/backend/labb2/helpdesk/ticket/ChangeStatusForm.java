package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.constraints.NotNull;

/**
 * What the "Change status" form sends: the chosen status and the ticket's version, so the
 * server can tell if someone else changed the ticket since the page was loaded. The ticket
 * comes from the URL and the agent from the login. A value that isn't a TicketStatus
 * becomes a binding error.
 */
public class ChangeStatusForm {

    @NotNull
    private TicketStatus status;

    @NotNull
    private Long version;

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
