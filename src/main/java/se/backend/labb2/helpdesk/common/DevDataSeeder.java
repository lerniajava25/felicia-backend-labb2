package se.backend.labb2.helpdesk.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.ticket.Comment;
import se.backend.labb2.helpdesk.ticket.CommentRepository;
import se.backend.labb2.helpdesk.ticket.Ticket;
import se.backend.labb2.helpdesk.ticket.TicketRepository;
import se.backend.labb2.helpdesk.ticket.TicketStatus;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DevDataSeeder(UserRepository userRepository,
                         TicketRepository ticketRepository,
                         CommentRepository commentRepository,
                         PasswordEncoder passwordEncoder,
                         Environment environment) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        String passwordHash = passwordEncoder.encode(environment.getRequiredProperty("app.seed.password"));

        User admin = new User("admin", passwordHash, "Admin", Role.ADMIN);
        User pippi = new User("pippi", passwordHash, "Pippi Långstrump", Role.AGENT);
        User billy = new User("billy", passwordHash, "Billy Bolinder", Role.USER);
        User olle = new User("olle", passwordHash, "Olle Olsson", Role.USER);
        List<User> users = userRepository.saveAll(List.of(admin, pippi, billy, olle));

        Ticket emergency = new Ticket("Big emergency!!",
                "There are no snacks", billy);
        emergency.assignTo(pippi);
        emergency.changeStatus(TicketStatus.IN_PROGRESS);

        Ticket signIn = new Ticket("Cannot sign in to computer",
                "The computer says my password has expired", billy);

        Ticket screen = new Ticket("Need a second screen",
                "I would like a second screen for my desk.", olle);

        List<Ticket> tickets = ticketRepository.saveAll(List.of(emergency, signIn, screen));

        commentRepository.saveAll(List.of(
                new Comment(emergency, billy, "Oh no"),
                new Comment(emergency, pippi, "Mr. Nilsson is on it")));

        log.info("Seeded {} users and {} tickets", users.size(), tickets.size());
    }
}
