package com.invoiceocr.domain;

import java.util.Objects;
import java.util.Optional;

/** One field definition bound to the value found for it (possibly none). */
public record ExtractedField(FieldDefinition definition, Optional<String> value) {

    public ExtractedField {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(value, "value");
    }

    public static ExtractedField of(FieldDefinition definition, String value) {
        return new ExtractedField(definition, Optional.ofNullable(value).filter(v -> !v.isBlank()));
    }

    public static ExtractedField missing(FieldDefinition definition) {
        return new ExtractedField(definition, Optional.empty());
    }

    public boolean isPresent() {
        return value.isPresent();
    }

    public String valueOr(String fallback) {
        return value.orElse(fallback);
    }
}
