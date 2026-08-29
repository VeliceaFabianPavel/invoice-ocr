package com.invoiceocr.domain;

import java.util.Objects;

/** Immutable carrier for the raw text produced by an OCR engine. */
public record RecognizedText(String value) {

    public static final RecognizedText EMPTY = new RecognizedText("");

    public RecognizedText {
        Objects.requireNonNull(value, "value");
    }

    /** Null-safe factory: {@code null} collapses to {@link #EMPTY}. */
    public static RecognizedText of(String raw) {
        return raw == null || raw.isEmpty() ? EMPTY : new RecognizedText(raw);
    }

    public boolean isBlank() {
        return value.isBlank();
    }
}
