package com.invoiceocr.extraction.normalization;

import java.util.Objects;

/**
 * Cleans a raw captured string into the form the report should show.
 *
 * <p>Normalisation is deliberately separate from matching: a regex decides
 * <em>where</em> a value is, a normaliser decides <em>how it should look</em>.
 * Both sides stay small and independently testable.</p>
 */
@FunctionalInterface
public interface ValueNormalizer {

    String normalize(String value);

    default ValueNormalizer andThen(ValueNormalizer next) {
        Objects.requireNonNull(next, "next");
        return value -> next.normalize(normalize(value));
    }

    static ValueNormalizer identity() {
        return value -> value;
    }
}
