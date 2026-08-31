package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.LineItem;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Machine-readable report, for exporting or piping into an ERP import.
 *
 * <p>Written by hand rather than with a JSON library: the shape is fixed and
 * small, and a dependency would buy nothing here.</p>
 *
 * <p>The document is nested rather than flat, which it was in 1.1:</p>
 *
 * <pre>
 *   { "fields":     { "supplier": "SC ALFA SRL", "iban": null, … },
 *     "confidence": { "supplier": 0.9, … },
 *     "lineItems":  [ { "description": …, "quantity": …, "value": … } ],
 *     "summary":    { "recognized": 9, "fields": 12, "averageConfidence": 0.87 } }
 * </pre>
 *
 * <p>Flat was the right shape while a field was a string and nothing else. It
 * stopped being so once the same document had to carry how much each value is
 * worth and the rows of the goods table, neither of which is a field. A consumer
 * written against 1.1 reads {@code fields} instead of the root object, and the
 * rest is additional.</p>
 */
public final class JsonInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String INDENT = "  ";
    private static final String NESTED = INDENT + INDENT;
    private static final String DEEP = NESTED + INDENT;

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        StringBuilder out = new StringBuilder("{\n");

        out.append(INDENT).append("\"fields\": ").append(fields(data)).append(",\n");
        out.append(INDENT).append("\"confidence\": ").append(confidence(data)).append(",\n");
        out.append(INDENT).append("\"lineItems\": ").append(lineItems(data)).append(",\n");
        out.append(INDENT).append("\"summary\": ").append(summary(data)).append("\n");

        return out.append("}").toString();
    }

    private static String fields(InvoiceData data) {
        StringJoiner entries = new StringJoiner(",\n", "{\n", "\n" + INDENT + "}");
        for (ExtractedField field : data.fields()) {
            String value = field.value().map(JsonInvoiceReportFormatter::quote).orElse("null");
            entries.add(NESTED + quote(field.definition().key()) + ": " + value);
        }
        return entries.toString();
    }

    /** Only fields that were found: a confidence of zero says nothing a null does not. */
    private static String confidence(InvoiceData data) {
        StringJoiner entries = new StringJoiner(",\n", "{\n", "\n" + INDENT + "}");
        boolean any = false;
        for (ExtractedField field : data.fields()) {
            if (field.isPresent()) {
                entries.add(NESTED + quote(field.definition().key()) + ": " + number(field.confidence()));
                any = true;
            }
        }
        return any ? entries.toString() : "{}";
    }

    private static String lineItems(InvoiceData data) {
        if (!data.hasLineItems()) {
            return "[]";
        }
        StringJoiner rows = new StringJoiner(",\n", "[\n", "\n" + INDENT + "]");
        for (LineItem item : data.lineItems()) {
            rows.add(NESTED + "{\n"
                    + DEEP + "\"description\": " + quote(item.description()) + ",\n"
                    + DEEP + "\"quantity\": " + item.quantity().map(JsonInvoiceReportFormatter::quote)
                            .orElse("null") + ",\n"
                    + DEEP + "\"unitPrice\": " + item.unitPrice().map(JsonInvoiceReportFormatter::quote)
                            .orElse("null") + ",\n"
                    + DEEP + "\"value\": " + quote(item.lineTotal()) + "\n"
                    + NESTED + "}");
        }
        return rows.toString();
    }

    private static String summary(InvoiceData data) {
        return "{\n"
                + NESTED + "\"recognized\": " + data.recognizedCount() + ",\n"
                + NESTED + "\"fields\": " + data.fields().size() + ",\n"
                + NESTED + "\"averageConfidence\": " + number(data.averageConfidence()) + ",\n"
                + NESTED + "\"needsReview\": " + reviewList(data) + "\n"
                + INDENT + "}";
    }

    private static String reviewList(InvoiceData data) {
        StringJoiner keys = new StringJoiner(", ", "[", "]");
        data.needingReview().forEach(field -> keys.add(quote(field.definition().key())));
        return keys.toString();
    }

    /** Two decimals, dot-separated, so the document does not depend on a locale. */
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
