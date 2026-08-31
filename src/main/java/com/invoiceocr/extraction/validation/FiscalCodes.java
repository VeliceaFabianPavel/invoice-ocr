package com.invoiceocr.extraction.validation;

import com.invoiceocr.extraction.text.OcrDigits;
import java.util.Optional;

/**
 * The control digit carried by every Romanian fiscal code (CUI/CIF).
 *
 * <p>A fiscal code is not an arbitrary number. Its last digit is computed from
 * the others against the fixed key {@code 753217532}, which means a misread or
 * misattributed code can be <em>detected</em> without any reference data at
 * all.</p>
 *
 * <pre>
 *   sum     = Σ digit[i] × key[i], the body right-aligned against the key
 *   control = (sum × 10) mod 11, with 10 folded to 0
 * </pre>
 *
 * <p>Detecting it is what matters. An invoice prints two fiscal codes in exactly
 * the same shape, and until now the only thing telling them apart was which
 * block they sat in — which fails the moment a layout puts them somewhere
 * unexpected. A control digit is independent of layout: of the two candidates,
 * the one that adds up is the one that was read correctly.</p>
 */
public final class FiscalCodes {

    private static final String KEY = "753217532";
    private static final int MAX_BODY = KEY.length();
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = MAX_BODY + 1;

    /** Below this a code is too short for the checksum to mean anything. */
    private static final int MIN_CHECKABLE = 4;

    /** True when {@code code} carries a control digit that adds up. */
    public static boolean isValid(String code) {
        String digits = digitsOf(code);
        if (digits.length() < MIN_LENGTH || digits.length() > MAX_LENGTH) {
            return false;
        }
        String body = digits.substring(0, digits.length() - 1);
        int control = digits.charAt(digits.length() - 1) - '0';
        return controlDigitFor(body) == control;
    }

    /** The control digit a body of digits should be followed by. */
    public static int controlDigitFor(String body) {
        String padded = "0".repeat(Math.max(0, MAX_BODY - body.length())) + body;
        int sum = 0;
        for (int i = 0; i < MAX_BODY; i++) {
            sum += (padded.charAt(i) - '0') * (KEY.charAt(i) - '0');
        }
        int control = (sum * 10) % 11;
        return control == 10 ? 0 : control;
    }

    /**
     * The digit reading of {@code raw}, when that reading's control digit adds up.
     *
     * <p>Inside a numeric field the character confusions run one way — a printed
     * {@code O} in a code is a zero, an {@code S} is a five — so there is a
     * single reading to test rather than a search space. What the checksum adds
     * is knowing whether that reading is right.</p>
     *
     * @return the repaired digits, prefixed {@code RO} when the input was, or
     *         empty when the repaired reading still does not validate
     */
    public static Optional<String> repair(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        boolean prefixed = hasCountryPrefix(raw);
        String body = OcrDigits.repair(prefixed ? stripCountryPrefix(raw) : raw)
                .replaceAll("[^0-9]", "");
        return isValid(body) ? Optional.of(prefixed ? "RO" + body : body) : Optional.empty();
    }

    /**
     * The check the fiscal-code rule is built on.
     *
     * <p>Codes too short to carry a meaningful checksum are passed through
     * unproven rather than rejected — a handful of very old registrations are
     * that short. Everything else either adds up or does not.</p>
     */
    public static ValueCheck check() {
        return value -> {
            String digits = digitsOf(value);
            if (digits.length() < MIN_CHECKABLE) {
                return Verdict.UNPROVEN;
            }
            return isValid(digits) ? Verdict.PROVEN : Verdict.DOUBTFUL;
        };
    }

    private static boolean hasCountryPrefix(String value) {
        String trimmed = value.trim();
        return trimmed.length() >= 2 && Character.toUpperCase(trimmed.charAt(0)) == 'R'
                && (Character.toUpperCase(trimmed.charAt(1)) == 'O' || trimmed.charAt(1) == '0');
    }

    private static String stripCountryPrefix(String value) {
        return value.trim().substring(2);
    }

    /** The digits of a code, with the country prefix and any separators removed. */
    private static String digitsOf(String code) {
        if (code == null) {
            return "";
        }
        String withoutPrefix = hasCountryPrefix(code) ? stripCountryPrefix(code) : code;
        StringBuilder digits = new StringBuilder(withoutPrefix.length());
        for (int i = 0; i < withoutPrefix.length(); i++) {
            char c = withoutPrefix.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    private FiscalCodes() {
        throw new AssertionError("No instances");
    }
}
