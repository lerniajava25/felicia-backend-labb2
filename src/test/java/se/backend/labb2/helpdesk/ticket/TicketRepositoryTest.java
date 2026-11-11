package se.backend.labb2.helpdesk.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TicketRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void findByCreatedByIdReturnsOnlyThatUsersTickets() {
        User first = userRepository.save(new User("first", "{noop}x", "First User", Role.USER));
        User second = userRepository.save(new User("second", "{noop}x", "Second User", Role.USER));

        ticketRepository.saveAll(List.of(
                new Ticket("First A", "Description", first),
                new Ticket("First B", "Description", first),
                new Ticket("Second A", "Description", second)));

        List<Ticket> result = ticketRepository.findByCreatedByIdOrderByLastModifiedDesc(first.getId());

        assertThat(result)
                .extracting(Ticket::getTitle)
                .containsExactlyInAnyOrder("First A", "First B");
    }
}
