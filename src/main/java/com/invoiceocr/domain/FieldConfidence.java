package com.invoiceocr.domain;

/**
 * How much a value is to be trusted, on a scale from 0 to 1.
 *
 * <p>Confidence is not a probability and does not pretend to be one. It is an
 * ordering, and the only thing the application asks of it is that a better way
 * of finding a value scores higher than a worse one. That is enough for the two
 * jobs it has: choosing between the answers several recognition passes gave for
 * the same field, and telling the user which figures deserve a second look.</p>
 *
 * <p>The bands are named after <em>how</em> a value was found, because that is
 * what actually predicts whether it is right. A number sitting beside its own
 * label is almost always correct; the largest amount on the page is a decent
 * guess and no more.</p>
 */
public final class FieldConfidence {

    /** Proved by a check of its own: a fiscal code whose control digit adds up. */
    public static final double VERIFIED = 1.00;

    /** Read from its own label, on the same line. The ordinary good case. */
    public static final double LABELLED = 0.90;

    /** Read under a label — a column heading, or a block layout. */
    public static final double NEARBY = 0.75;

    /** Computed from other fields that are known: {@code total - net} is the VAT. */
    public static final double DERIVED = 0.70;

    /** Recognised by its own shape inside the right part of the page. */
    public static final double SHAPED = 0.55;

    /** Recognised by shape anywhere on the page, with nothing to confirm it. */
    public static final double LOOSE = 0.45;

    /** A gated guess: the largest amount is the total, on a page that mentions one. */
    public static final double INFERRED = 0.35;

    /** Below this a value is shown, but flagged for the user to check. */
    public static final double REVIEW_THRESHOLD = 0.60;

    /** Multiplier applied to each further rung when a rule does not state its own. */
    public static final double LADDER_DECAY = 0.85;

    /** Clamps an arbitrary number into the 0..1 range this scale is defined on. */
    public static double clamp(double confidence) {
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /** True when a value should be shown with a "check this" marker. */
    public static boolean needsReview(double confidence) {
        return confidence > 0.0 && confidence < REVIEW_THRESHOLD;
    }

    private FieldConfidence() {
        throw new AssertionError("No instances");
    }
}
