package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.Objects;

/**
 * Markdown: a heading, a field table, the goods table, and the raw text in a
 * fenced block.
 *
 * <p>Values that were inferred rather than read carry the same mark the on-screen
 * report uses, so a Markdown export pasted into a ticket or a wiki still says
 * which figures the reader should check.</p>
 */
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
        String mark = messages.get(MessageKeys.REPORT_REVIEW_MARK);
        boolean anyMarked = false;
        for (ExtractedField field : data.fields()) {
            out.append("| ").append(escape(messages.get(field.definition().labelKey())))
                    .append(" | ").append(escape(field.valueOr(missing)));
            if (field.needsReview()) {
                out.append(' ').append(escape(mark));
                anyMarked = true;
            }
            out.append(" |").append(NL);
        }

        out.append(NL)
                .append(messages.get(MessageKeys.REPORT_FOOTER, data.recognizedCount(), data.fields().size()))
                .append(NL);

        if (anyMarked) {
            out.append(NL).append("> ")
                    .append(messages.get(MessageKeys.REPORT_REVIEW_HINT).replace("\n", NL + "> "))
                    .append(NL);
        }

        if (data.hasLineItems()) {
            appendLineItems(out, data, missing);
        }

        if (!data.source().isBlank()) {
            out.append(NL).append("## ").append(messages.get(MessageKeys.REPORT_RAW_TEXT)).append(NL).append(NL);
            out.append("```text").append(NL).append(data.source().value()).append(NL).append("```").append(NL);
        }
        return out.toString();
    }

    private void appendLineItems(StringBuilder out, InvoiceData data, String missing) {
        out.append(NL).append("## ").append(messages.get(MessageKeys.REPORT_ITEMS_TITLE))
                .append(NL).append(NL);
        out.append("| ").append(messages.get(MessageKeys.REPORT_ITEMS_DESCRIPTION))
                .append(" | ").append(messages.get(MessageKeys.REPORT_ITEMS_QUANTITY))
                .append(" | ").append(messages.get(MessageKeys.REPORT_ITEMS_UNIT_PRICE))
                .append(" | ").append(messages.get(MessageKeys.REPORT_ITEMS_TOTAL))
                .append(" |").append(NL);
        out.append("|---|---:|---:|---:|").append(NL);

        for (LineItem item : data.lineItems()) {
            out.append("| ").append(escape(item.description()))
                    .append(" | ").append(escape(item.quantityOr(missing)))
                    .append(" | ").append(escape(item.unitPriceOr(missing)))
                    .append(" | ").append(escape(item.lineTotal()))
                    .append(" |").append(NL);
        }
    }

    /** A pipe inside a cell would end the column, and a backslash would escape. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }
}
