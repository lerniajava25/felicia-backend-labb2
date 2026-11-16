package se.backend.labb2.helpdesk.ticket;

import se.backend.labb2.helpdesk.common.DateTimes;

import java.util.List;

/**
 * Everything the details page shows about one ticket. Built inside the service
 * transaction, where the lazy createdBy, assignee and comment authors can still be read.
 *
 * @param assignee the assignee's display name, or null when nobody is assigned
 */
public record TicketDetails(Long id,
                            String title,
                            String description,
                            TicketStatus status,
                            String createdBy,
                            String assignee,
                            String created,
                            String lastModified,
                            List<CommentView> comments) {

    public static TicketDetails from(Ticket ticket, List<Comment> comments) {
        return new TicketDetails(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCreatedBy().getDisplayName(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getDisplayName(),
                DateTimes.format(ticket.getCreatedAt()),
                DateTimes.format(ticket.getLastModified()),
                comments.stream().map(CommentView::from).toList());
    }
}
