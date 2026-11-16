package se.backend.labb2.helpdesk.ticket;

import se.backend.labb2.helpdesk.common.DateTimes;

/**
 * One line in the agents' list of all tickets. Like TicketRow, plus who created it and who
 * it is assigned to. Built inside the service transaction (createdBy and assignee are lazy).
 *
 * @param assignee the assignee's display name, or null when nobody is assigned
 */
public record AgentTicketRow(Long id,
                             String title,
                             TicketStatus status,
                             String createdBy,
                             String assignee,
                             String lastModified) {

    public static AgentTicketRow from(Ticket ticket) {
        return new AgentTicketRow(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getCreatedBy().getDisplayName(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getDisplayName(),
                DateTimes.format(ticket.getLastModified()));
    }
}
