package se.backend.labb2.helpdesk.ticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.common.NotFoundException;
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
