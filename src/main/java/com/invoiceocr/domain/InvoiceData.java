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
 * The result of parsing one invoice: the raw OCR text, every field the parser
 * was asked to look for in display order, and the rows of the goods table when
 * one was found.
 *
 * <p>Missing fields are kept as empty {@link ExtractedField}s rather than being
 * dropped, so a renderer can decide how absence should look without knowing the
 * field catalog.</p>
 */
public final class InvoiceData {

    private final RecognizedText source;
    private final Map<String, ExtractedField> fieldsByKey;
    private final List<LineItem> lineItems;

    private InvoiceData(RecognizedText source, Map<String, ExtractedField> fieldsByKey,
                        List<LineItem> lineItems) {
        this.source = source;
        this.fieldsByKey = fieldsByKey;
        this.lineItems = lineItems;
    }

    public static InvoiceData of(RecognizedText source, Collection<ExtractedField> fields) {
        return of(source, fields, List.of());
    }

    public static InvoiceData of(RecognizedText source, Collection<ExtractedField> fields,
                                 List<LineItem> lineItems) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(lineItems, "lineItems");
        Map<String, ExtractedField> ordered = fields.stream()
                .sorted((a, b) -> a.definition().compareTo(b.definition()))
                .collect(Collectors.toMap(
                        field -> field.definition().key(),
                        field -> field,
                        (first, second) -> second,
                        LinkedHashMap::new));
        return new InvoiceData(source, Collections.unmodifiableMap(ordered), List.copyOf(lineItems));
    }

    public static InvoiceData empty() {
        return new InvoiceData(RecognizedText.EMPTY, Map.of(), List.of());
    }

    /** The same data with its table rows replaced; the fields are untouched. */
    public InvoiceData withLineItems(List<LineItem> replacement) {
        return new InvoiceData(source, fieldsByKey, List.copyOf(replacement));
    }

    /** The same fields carried over a different recognition of the same page. */
    public InvoiceData withSource(RecognizedText replacement) {
        return new InvoiceData(Objects.requireNonNull(replacement, "replacement"), fieldsByKey, lineItems);
    }

    public RecognizedText source() {
        return source;
    }

    /** Fields in display order, present and missing alike. */
    public List<ExtractedField> fields() {
        return List.copyOf(fieldsByKey.values());
    }

    /** Rows of the goods table, in the order they were printed. Possibly empty. */
    public List<LineItem> lineItems() {
        return lineItems;
    }

    public boolean hasLineItems() {
        return !lineItems.isEmpty();
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

    /** Fields worth a second look: found, but by a strategy that guesses. */
    public List<ExtractedField> needingReview() {
        return fieldsByKey.values().stream().filter(ExtractedField::needsReview).toList();
    }

    /**
     * Mean confidence over the fields that were found, or 0 when none were.
     *
     * <p>Missing fields are excluded on purpose: an invoice that prints no IBAN
     * is not a worse reading than one that does, and averaging in a zero for
     * every absent field would say that it is.</p>
     */
    public double averageConfidence() {
        return fieldsByKey.values().stream()
                .filter(ExtractedField::isPresent)
                .mapToDouble(ExtractedField::confidence)
                .average()
                .orElse(0.0);
    }

    @Override
    public String toString() {
        return "InvoiceData" + fieldsByKey.values()
                + (lineItems.isEmpty() ? "" : " + " + lineItems.size() + " line items");
    }
}
