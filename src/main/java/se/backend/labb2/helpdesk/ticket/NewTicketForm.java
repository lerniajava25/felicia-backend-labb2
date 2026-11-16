package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the "New ticket" form may send. Only these two fields: owner, status and
 * assignee are set by the server, so extra request parameters have nothing to bind to.
 * Max lengths match the columns in Ticket.
 */
public class NewTicketForm {

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title can be at most 120 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description can be at most 5000 characters")
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
