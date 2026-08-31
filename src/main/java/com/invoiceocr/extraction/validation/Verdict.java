package com.invoiceocr.extraction.validation;

/**
 * What a {@link ValueCheck} concluded about a candidate value.
 *
 * <p>Four answers rather than a boolean, because the useful question is not
 * whether a value passed but what should happen to it if it did not — and that
 * differs by field.</p>
 *
 * <p>A fiscal code that fails its control digit is probably the right field
 * misread, and showing it with a warning beats showing nothing: the user can
 * compare it against the raw text in seconds. An IBAN that fails mod-97 is a
 * different matter. The pattern that finds one is deliberately loose, because a
 * strict pattern misses accounts printed in unusual groupings, so the checksum
 * is not confirming a match — it <em>is</em> the match. Without it there is no
 * reason to think the text was ever an account number, and a wrong bank account
 * on an invoice is the one error that costs real money.</p>
 */
public enum Verdict {

    /** Checked and correct: a control digit that adds up, an IBAN that passes mod-97. */
    PROVEN,

    /** Nothing to check it against, or the check does not apply. Keep it as it is. */
    UNPROVEN,

    /**
     * Checked and wrong, but recognisably the right kind of value. Kept as a
     * last resort, discounted and flagged, so the user sees what was read.
     */
    DOUBTFUL,

    /** Not a value of this kind at all. Discarded, however little else there is. */
    IMPOSSIBLE;

    /** True when a better candidate should be preferred to this one. */
    public boolean isSuspect() {
        return this == DOUBTFUL || this == IMPOSSIBLE;
    }

    public boolean isProven() {
        return this == PROVEN;
    }
}
