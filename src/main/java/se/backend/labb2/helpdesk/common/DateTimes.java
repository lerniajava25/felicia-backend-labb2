package se.backend.labb2.helpdesk.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One date format for every page: "2026-11-16 14:05", Swedish local time. */
public final class DateTimes {

    // Instant has no time zone, so the formatter needs one. Immutable, so safe to share.
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Stockholm"));

    private DateTimes() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }
}
