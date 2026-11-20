package se.backend.labb2.helpdesk.ticket;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A ticket board: one column per status, in TicketStatus order. Generic in the row type,
 * so a user's board (TicketRow) and the agents' boards (AgentTicketRow) are built the same way.
 *
 * @param empty true when no column has any tickets, so the template needs no logic
 */
public record Board<R>(List<BoardColumn<R>> columns, boolean empty) {

    /**
     * One column per status in TicketStatus order, empty columns included. Each column keeps
     * the rows' order, so a newest-first list gives newest-first columns.
     */
    public static <R> Board<R> of(List<R> rows, Function<R, TicketStatus> statusOf) {
        // groupingBy keeps the list's order inside each group, so newest first survives.
        Map<TicketStatus, List<R>> byStatus = rows.stream().collect(Collectors.groupingBy(statusOf));

        List<BoardColumn<R>> columns = Arrays.stream(TicketStatus.values())
                .map(status -> new BoardColumn<>(status, status.label(), byStatus.getOrDefault(status, List.of())))
                .toList();
        return new Board<>(columns, rows.isEmpty());
    }
}
