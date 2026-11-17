package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the "Change status" form sends: the chosen status and the ticket's version, so the
 * server can tell if someone else changed the ticket since the page was loaded. The ticket
 * comes from the URL and the agent from the login. A value that isn't a TicketStatus
 * becomes a binding error.
 *
 * The reason is only needed when a NEW ticket is closed; the service checks that, because
 * it depends on the ticket's current status. It is saved as the comment "Closed: " + reason,
 * so 1990 + 8 stays within the 2000-character comment column.
 */
public class ChangeStatusForm {

    @NotNull
    private TicketStatus status;

    @Size(max = 1990)
    private String reason;

    @NotNull
    private Long version;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

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
