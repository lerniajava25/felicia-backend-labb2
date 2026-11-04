package se.backend.labb2.helpdesk.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = {"createdBy", "assignee"})
    List<Ticket> findByCreatedByIdOrderByLastModifiedDesc(Long userId);

    @EntityGraph(attributePaths = {"createdBy", "assignee"})
    List<Ticket> findAllByOrderByLastModifiedDesc();

    @EntityGraph(attributePaths = {"createdBy", "assignee"})
    Optional<Ticket> findWithPeopleById(Long id);
}
