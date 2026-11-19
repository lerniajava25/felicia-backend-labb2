package se.backend.labb2.helpdesk.ticket;

import se.backend.labb2.helpdesk.common.DateTimes;

import java.util.List;

/**
 * Everything the details page shows about one ticket. Built inside the service
 * transaction, where the lazy createdBy, assignee and comment authors can still be read.
 *
 * @param version  sent back by forms that change the ticket, to detect someone else's change
 * @param assignee the assignee's display name, or null when nobody is assigned
 * @param assigneeId the assignee's user id, or null; lets the page compare it with the logged-in user
 * @param allowedMoves the statuses this ticket may move to now (empty for CLOSED), built by the service
 */
public record TicketDetails(Long id,
                            Long version,
                            String title,
                            String description,
                            TicketStatus status,
                            String createdBy,
                            String assignee,
                            Long assigneeId,
                            String created,
                            String lastModified,
                            List<CommentView> comments,
                            List<TicketStatus> allowedMoves) {

    public static TicketDetails from(Ticket ticket, List<Comment> comments, List<TicketStatus> allowedMoves) {
        return new TicketDetails(
                ticket.getId(),
                ticket.getVersion(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCreatedBy().getDisplayName(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getDisplayName(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                DateTimes.format(ticket.getCreatedAt()),
                DateTimes.format(ticket.getLastModified()),
                comments.stream().map(CommentView::from).toList(),
                allowedMoves);
    }
}
