package se.backend.labb2.helpdesk.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.backend.labb2.helpdesk.ticket.TicketStatus.CLOSED;
import static se.backend.labb2.helpdesk.ticket.TicketStatus.IN_PROGRESS;
import static se.backend.labb2.helpdesk.ticket.TicketStatus.NEW;
import static se.backend.labb2.helpdesk.ticket.TicketStatus.RESOLVED;
import static se.backend.labb2.helpdesk.ticket.TicketStatus.WAITING_FOR_USER;

class TicketStatusTest {

    // The only allowed moves. Every other combination must be rejected.
    private static final List<List<TicketStatus>> ALLOWED = List.of(
            List.of(NEW, IN_PROGRESS),
            List.of(NEW, CLOSED),
            List.of(IN_PROGRESS, WAITING_FOR_USER),
            List.of(IN_PROGRESS, RESOLVED),
            List.of(WAITING_FOR_USER, IN_PROGRESS),
            List.of(WAITING_FOR_USER, RESOLVED),
            List.of(RESOLVED, CLOSED),
            List.of(RESOLVED, IN_PROGRESS));

    static Stream<Arguments> allowedMoves() {
        return ALLOWED.stream().map(move -> Arguments.of(move.get(0), move.get(1)));
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("allowedMoves")
    void allowedMoveIsTrue(TicketStatus from, TicketStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @Test
    void everyOtherMoveIsFalse() {
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                if (!ALLOWED.contains(List.of(from, to))) {
                    assertThat(from.canMoveTo(to))
                            .as("%s -> %s should not be allowed", from, to)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void changeStatusFromClosedToNewThrows() {
        User user = new User("kalle", "{noop}x", "Kalle Anka", Role.USER);
        Ticket ticket = new Ticket("Title", "Description", user);
        ticket.changeStatus(CLOSED);

        assertThatThrownBy(() -> ticket.changeStatus(NEW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ticket.getStatus()).isEqualTo(CLOSED);
    }
}
