package com.invoiceocr.extraction.validation;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Whether a normalised date is a date at all.
 *
 * <p>The pattern that finds dates has to be generous — it is reading a scan —
 * so it accepts {@code 31.02.2024} and {@code 14.14.2024}, which are shapes but
 * not days. The calendar itself is the check, and it is a strong one: roughly
 * one in eight impossible readings is caught by month alone, and every one of
 * them would otherwise have been printed on the report as fact.</p>
 *
 * <p>Rejecting a bad date is not the same as having none. The rule that reads
 * the issue date has four rungs; when the first returns "14.14.2024" the check
 * sends it back and the second rung's answer is used instead.</p>
 */
public final class Dates {

    /**
     * The form the normalisers produce, and the only one parsed here.
     *
     * <p>Strict resolution, which is the entire point. The default would quietly
     * take 31 February to be 29 February and hand back a date, which is exactly
     * the silent correction this class exists to prevent: an invoice dated to a
     * day that does not exist has been misread, and the reading should be
     * refused rather than rounded into plausibility.</p>
     */
    public static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Nothing before this is a plausible invoice date; scanning artefacts land far outside. */
    private static final int EARLIEST_YEAR = 1990;
    private static final int LATEST_YEAR = 2100;

    /** The date, when the text is one. */
    public static Optional<LocalDate> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(value.trim(), FORMAT);
            return date.getYear() >= EARLIEST_YEAR && date.getYear() <= LATEST_YEAR
                    ? Optional.of(date)
                    : Optional.empty();
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /** Renders a date back into the form the rest of the application uses. */
    public static String format(LocalDate date) {
        return date.format(FORMAT);
    }

    /**
     * Rejects anything that is not a real day.
     *
     * <p>Text that never reached the canonical form is passed through unproven
     * rather than rejected: the normaliser hands back what OCR read when it
     * cannot make sense of it, and showing the operator that is more useful than
     * discarding it.</p>
     */
    public static ValueCheck check() {
        return value -> {
            if (value == null || !value.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                return Verdict.UNPROVEN;
            }
            return parse(value).isPresent() ? Verdict.PROVEN : Verdict.DOUBTFUL;
        };
    }

    private Dates() {
        throw new AssertionError("No instances");
    }
}
