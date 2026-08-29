package com.invoiceocr.extraction.text;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Turns a printed amount into a canonical one, and into a number when a
 * comparison is needed.
 *
 * <p>The rule is positional rather than locale-based: the last separator is a
 * decimal point only when exactly two digits follow it, and every other
 * separator is a thousands separator. That single rule reads {@code 1.190,00},
 * {@code 1,190.00} and {@code 1 190,00} identically, without having to know
 * which country printed the invoice.</p>
 */
public final class Amounts {

    private static final String SEPARATORS = ".,";

    /** Canonical form: a dot for decimals, nothing for thousands. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("[\\s ]", "");
        if (compact.isEmpty()) {
            return "";
        }
        int lastSeparator = lastSeparatorIndex(compact);
        if (lastSeparator < 0) {
            return compact;
        }
        String tail = compact.substring(lastSeparator + 1);
        if (tail.length() == 2 && isDigits(tail)) {
            return removeSeparators(compact.substring(0, lastSeparator)) + "." + tail;
        }
        return removeSeparators(compact);
    }

    /** The numeric value, after repairing OCR digit confusions. Empty if it is not a number. */
    public static Optional<BigDecimal> toNumber(String value) {
        String normalized = normalize(OcrDigits.repair(value == null ? "" : value));
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int lastSeparatorIndex(String value) {
        for (int i = value.length() - 1; i >= 0; i--) {
            if (SEPARATORS.indexOf(value.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String removeSeparators(String value) {
        return value.replaceAll("[.,]", "");
    }

    private static boolean isDigits(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private Amounts() {
        throw new AssertionError("No instances");
    }
}
