package com.invoiceocr.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One field definition bound to the value found for it (possibly none),
 * together with how the value was found and how much it is worth.
 *
 * <p>The provenance is not decoration. When several recognition passes disagree
 * about the same field, the only way to choose between them is to know how each
 * answer was arrived at — and when the user is deciding which figures to check
 * by hand, the same information is what tells them where to look.</p>
 *
 * @param definition the field this value belongs to
 * @param value      the value, or empty when nothing was found
 * @param confidence 0 for a missing field, otherwise a {@link FieldConfidence} band
 * @param strategy   short name of what produced it, for diagnostics and merging
 */
public record ExtractedField(FieldDefinition definition, Optional<String> value,
                             double confidence, String strategy) {

    /** Used where a value has no history worth recording — hand-built data, tests. */
    public static final String DIRECT = "direct";

    public ExtractedField {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(strategy, "strategy");
        confidence = value.isPresent() ? FieldConfidence.clamp(confidence) : 0.0;
    }

    public static ExtractedField of(FieldDefinition definition, String value) {
        return of(definition, value, FieldConfidence.VERIFIED, DIRECT);
    }

    public static ExtractedField of(FieldDefinition definition, String value,
                                    double confidence, String strategy) {
        Optional<String> present = Optional.ofNullable(value).filter(v -> !v.isBlank());
        return new ExtractedField(definition, present, confidence, strategy);
    }

    public static ExtractedField missing(FieldDefinition definition) {
        return new ExtractedField(definition, Optional.empty(), 0.0, "none");
    }

    public boolean isPresent() {
        return value.isPresent();
    }

    public String valueOr(String fallback) {
        return value.orElse(fallback);
    }

    /** True when the value is present but was found in a way worth double-checking. */
    public boolean needsReview() {
        return isPresent() && FieldConfidence.needsReview(confidence);
    }

    /** The same value, re-rated — used when a later check confirms or doubts it. */
    public ExtractedField ratedAt(double newConfidence, String newStrategy) {
        return new ExtractedField(definition, value, newConfidence, newStrategy);
    }
}
