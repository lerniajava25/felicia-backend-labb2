package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.constraints.NotNull;

/**
 * What the "Assign to me" button sends: only the ticket's version, so the server can tell
 * if someone else changed the ticket since the page was loaded. The ticket comes from the
 * URL and the agent from the login.
 */
public class AssignForm {

    @NotNull
    private Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
