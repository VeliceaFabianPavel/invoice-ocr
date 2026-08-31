package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldConfidence;
import java.util.Objects;

/**
 * What an extractor found, and how it found it.
 *
 * <p>Returning a bare string was enough while one pass over one image produced
 * one answer per field. It stopped being enough the moment several passes could
 * each produce a different answer: choosing between {@code 1.190,00} and
 * {@code l.190,00} needs to know that one came from beside its label and the
 * other from a shape match halfway down the page.</p>
 *
 * @param value      the text as printed, sliced out of the original page
 * @param confidence a {@link FieldConfidence} band
 * @param strategy   short name of the strategy, carried into diagnostics
 */
public record Extraction(String value, double confidence, String strategy) {

    public Extraction {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(strategy, "strategy");
        confidence = FieldConfidence.clamp(confidence);
    }

    /** A find by {@code strategy}, rated at the band that strategy usually earns. */
    public static Extraction of(String value, double confidence, String strategy) {
        return new Extraction(value, confidence, strategy);
    }

    /** The same find, re-rated. Used by the ladder and by the validators. */
    public Extraction ratedAt(double newConfidence) {
        return new Extraction(value, newConfidence, strategy);
    }

    /** The same find, re-rated by a factor. Used to discount a lower rung. */
    public Extraction scaledBy(double factor) {
        return new Extraction(value, confidence * factor, strategy);
    }

    /** The same find with a different value — a normaliser's or a repairer's output. */
    public Extraction withValue(String replacement) {
        return new Extraction(replacement, confidence, strategy);
    }

    /** The same find, credited to a strategy that wrapped this one. */
    public Extraction via(String outerStrategy) {
        return new Extraction(value, confidence, outerStrategy + "/" + strategy);
    }
}
