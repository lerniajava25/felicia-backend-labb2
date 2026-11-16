package se.backend.labb2.helpdesk.ticket;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.common.NotFoundException;
import se.backend.labb2.helpdesk.common.RuleViolationException;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository,
                         CommentRepository commentRepository,
                         UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a ticket owned by the logged-in user and returns its id.
     * The owner comes from the login, never from the form; status starts as NEW, no assignee.
     */
    @Transactional
    public Long create(NewTicketForm form, CurrentUser current) {
        // A reference is enough for the foreign key: no SELECT for the user.
        User creator = userRepository.getReferenceById(current.id());
        Ticket ticket = new Ticket(form.getTitle().trim(), form.getDescription(), creator);
        return ticketRepository.save(ticket).getId();
    }

    /**
     * Every ticket, newest change first, for agents (and admins, through the role hierarchy).
     * The URL rule already guards /agent/**; this protects the method itself too.
     */
    @PreAuthorize("hasRole('AGENT')")
    public List<AgentTicketRow> listAll() {
        // The repository's entity graph loads createdBy and assignee in the same query.
        return ticketRepository.findAllByOrderByLastModifiedDesc().stream()
                .map(AgentTicketRow::from)
                .toList();
    }

    /**
     * Every ticket as a board for agents: one column per status, in TicketStatus order,
     * empty columns included. Each column is newest change first. Same idea as boardFor.
     */
    @PreAuthorize("hasRole('AGENT')")
    public AgentBoard boardOfAll() {
        List<AgentTicketRow> rows = listAll();
        // groupingBy keeps the list's order inside each group, so newest first survives.
        Map<TicketStatus, List<AgentTicketRow>> byStatus = rows.stream()
                .collect(Collectors.groupingBy(AgentTicketRow::status));

        List<AgentBoardColumn> columns = Arrays.stream(TicketStatus.values())
                .map(status -> new AgentBoardColumn(status, status.label(), byStatus.getOrDefault(status, List.of())))
                .toList();
        return new AgentBoard(columns, rows.isEmpty());
    }

    /**
     * The current user's own tickets as a board: one column per status, in TicketStatus
     * order, empty columns included. Each column is newest change first.
     */
    public Board boardFor(CurrentUser current) {
        List<TicketRow> rows = ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(current.id()).stream()
                .map(TicketRow::from)
                .toList();
        // groupingBy keeps the stream's order inside each group, so newest first survives.
        Map<TicketStatus, List<TicketRow>> byStatus = rows.stream()
                .collect(Collectors.groupingBy(TicketRow::status));

        List<BoardColumn> columns = Arrays.stream(TicketStatus.values())
                .map(status -> new BoardColumn(status, status.label(), byStatus.getOrDefault(status, List.of())))
                .toList();
        return new Board(columns, rows.isEmpty());
    }

    /**
     * Adds a comment by the logged-in user. Goes through loadVisible first, so a ticket
     * you may not see gives 404 and nothing is saved.
     */
    @Transactional
    public void addComment(Long ticketId, CommentForm form, CurrentUser current) {
        Ticket ticket = loadVisible(ticketId, current);
        User author = userRepository.getReferenceById(current.id());   // from the login, never the form
        commentRepository.save(new Comment(ticket, author, form.getBody()));
    }

    /**
     * The logged-in agent takes the ticket. A NEW ticket moves to IN_PROGRESS; other
     * statuses stay. Refuses a version that isn't the current one, and CLOSED tickets.
     */
    @Transactional
    @PreAuthorize("hasRole('AGENT')")
    public void assignToMe(Long id, Long version, CurrentUser current) {
        Ticket ticket = loadVisible(id, current);
        // Someone changed the ticket after the page was loaded: change nothing.
        if (!ticket.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, id);
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new RuleViolationException("A closed ticket cannot be assigned.");
        }
        ticket.assignTo(userRepository.getReferenceById(current.id()));
        if (ticket.getStatus() == TicketStatus.NEW) {
            ticket.changeStatus(TicketStatus.IN_PROGRESS);
        }
        // No save(): the ticket is managed, so Hibernate writes the changes at commit with
        // "UPDATE ... WHERE version = ?". If another agent got there first in the meantime,
        // that update hits 0 rows and Spring throws ObjectOptimisticLockingFailureException.
    }

    /** One ticket with its comments, oldest comment first. */
    public TicketDetails details(Long id, CurrentUser current) {
        Ticket ticket = loadVisible(id, current);
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return TicketDetails.from(ticket, comments);
    }

    /**
     * The only way to load a single ticket. A USER may only see tickets they created;
     * AGENT and ADMIN may see all. "Not yours" throws the same exception as "doesn't exist",
     * so trying ids reveals nothing.
     */
    private Ticket loadVisible(Long id, CurrentUser current) {
        Ticket ticket = ticketRepository.findWithPeopleById(id).orElseThrow(NotFoundException::new);
        // CurrentUser.role is a single value; the role hierarchy only applies to Spring
        // Security checks. So "is USER" is the right test here, not "is not AGENT".
        if (current.role() == Role.USER && !ticket.isCreatedBy(current.id())) {
            throw new NotFoundException();
        }
        return ticket;
    }
}
