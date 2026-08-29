package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.Objects;

/** Markdown: a heading, a field table, and the raw text in a fenced block. */
public final class MarkdownInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String NL = "\n";

    private final MessageSource messages;

    public MarkdownInvoiceReportFormatter(MessageSource messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        StringBuilder out = new StringBuilder();

        out.append("# ").append(messages.get(MessageKeys.REPORT_HEADER)).append(NL).append(NL);
        out.append("| ").append(messages.get(MessageKeys.REPORT_COLUMN_FIELD))
                .append(" | ").append(messages.get(MessageKeys.REPORT_COLUMN_VALUE)).append(" |").append(NL);
        out.append("|---|---|").append(NL);

        String missing = messages.get(MessageKeys.REPORT_MISSING_VALUE);
        for (ExtractedField field : data.fields()) {
            out.append("| ").append(escape(messages.get(field.definition().labelKey())))
                    .append(" | ").append(escape(field.valueOr(missing)))
                    .append(" |").append(NL);
        }

        out.append(NL)
                .append(messages.get(MessageKeys.REPORT_FOOTER, data.recognizedCount(), data.fields().size()))
                .append(NL);

        if (!data.source().isBlank()) {
            out.append(NL).append("## ").append(messages.get(MessageKeys.REPORT_RAW_TEXT)).append(NL).append(NL);
            out.append("```text").append(NL).append(data.source().value()).append(NL).append("```").append(NL);
        }
        return out.toString();
    }

    /** A pipe inside a cell would end the column, and a backslash would escape. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }
}
