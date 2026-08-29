package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Machine-readable report, for exporting or piping into an ERP import.
 *
 * <p>Written by hand rather than with a JSON library: the shape is a flat object
 * of string-or-null values, and a dependency would buy nothing here.</p>
 */
public final class JsonInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String INDENT = "  ";

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        StringJoiner entries = new StringJoiner(",\n", "{\n", "\n}");
        for (ExtractedField field : data.fields()) {
            String value = field.value().map(JsonInvoiceReportFormatter::quote).orElse("null");
            entries.add(INDENT + quote(field.definition().key()) + ": " + value);
        }
        return entries.toString();
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
