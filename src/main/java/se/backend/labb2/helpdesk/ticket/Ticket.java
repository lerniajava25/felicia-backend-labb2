package se.backend.labb2.helpdesk.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import se.backend.labb2.helpdesk.common.BaseEntity;
import se.backend.labb2.helpdesk.user.User;

@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    protected Ticket() {
    }

    public Ticket(String title, String description, User createdBy) {
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getAssignee() {
        return assignee;
    }

    public void assignTo(User assignee) {
        this.assignee = assignee;
    }

    public void changeStatus(TicketStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Cannot move ticket from " + status + " to " + next);
        }
        this.status = next;
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy.getId().equals(userId);
    }
}
