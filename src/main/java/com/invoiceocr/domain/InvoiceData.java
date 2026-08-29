package com.invoiceocr.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The result of parsing one invoice: the raw OCR text plus every field the
 * parser was asked to look for, in display order.
 *
 * <p>Missing fields are kept as empty {@link ExtractedField}s rather than being
 * dropped, so a renderer can decide how absence should look without knowing the
 * field catalog.</p>
 */
public final class InvoiceData {

    private final RecognizedText source;
    private final Map<String, ExtractedField> fieldsByKey;

    private InvoiceData(RecognizedText source, Map<String, ExtractedField> fieldsByKey) {
        this.source = source;
        this.fieldsByKey = fieldsByKey;
    }

    public static InvoiceData of(RecognizedText source, Collection<ExtractedField> fields) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fields, "fields");
        Map<String, ExtractedField> ordered = fields.stream()
                .sorted((a, b) -> a.definition().compareTo(b.definition()))
                .collect(Collectors.toMap(
                        field -> field.definition().key(),
                        field -> field,
                        (first, second) -> second,
                        LinkedHashMap::new));
        return new InvoiceData(source, Collections.unmodifiableMap(ordered));
    }

    public static InvoiceData empty() {
        return new InvoiceData(RecognizedText.EMPTY, Map.of());
    }

    public RecognizedText source() {
        return source;
    }

    /** Fields in display order, present and missing alike. */
    public List<ExtractedField> fields() {
        return List.copyOf(fieldsByKey.values());
    }

    public Optional<ExtractedField> field(FieldDefinition definition) {
        return Optional.ofNullable(fieldsByKey.get(definition.key()));
    }

    public Optional<String> valueOf(FieldDefinition definition) {
        return field(definition).flatMap(ExtractedField::value);
    }

    /** Number of fields that actually carry a value. */
    public long recognizedCount() {
        return fieldsByKey.values().stream().filter(ExtractedField::isPresent).count();
    }

    @Override
    public String toString() {
        return "InvoiceData" + fieldsByKey.values();
    }
}
