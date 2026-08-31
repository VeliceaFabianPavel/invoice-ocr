package com.invoiceocr.extraction.validation;

/**
 * Decides whether a normalised value can be what it claims to be.
 *
 * <p>This is where the recogniser stops guessing and starts knowing. A fiscal
 * code carries a control digit, an IBAN carries a mod-97 checksum, a date has to
 * exist in the calendar — each of those is a fact about the value that no amount
 * of pattern-matching can supply, and each of them turns a plausible reading
 * into a proven one, or rules it out entirely.</p>
 *
 * <p>Ruling one out is the more valuable half. Without a check, the first
 * candidate the ladder finds is the answer; with one, a candidate that fails is
 * skipped and the next rung gets its turn. That is what lets the buyer's fiscal
 * code lose to the supplier's on a page that prints them in the wrong order.</p>
 */
@FunctionalInterface
public interface ValueCheck {

    Verdict check(String value);

    /** The check that has no opinion, used by every field with nothing to verify. */
    static ValueCheck none() {
        return value -> Verdict.UNPROVEN;
    }

    /**
     * Both checks must pass.
     *
     * <p>The worse verdict wins, so one check calling a value impossible settles
     * it however sure the other was. Where neither objects, one proof is enough
     * to prove it: the checks test different things, and a value that satisfies
     * either has been verified by something.</p>
     */
    default ValueCheck and(ValueCheck next) {
        return value -> {
            Verdict mine = check(value);
            Verdict theirs = next.check(value);
            if (mine == Verdict.IMPOSSIBLE || theirs == Verdict.IMPOSSIBLE) {
                return Verdict.IMPOSSIBLE;
            }
            if (mine == Verdict.DOUBTFUL || theirs == Verdict.DOUBTFUL) {
                return Verdict.DOUBTFUL;
            }
            return mine.isProven() || theirs.isProven() ? Verdict.PROVEN : Verdict.UNPROVEN;
        };
    }
}
