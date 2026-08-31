package com.invoiceocr.format;

import com.invoiceocr.config.ReportSettings;
import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.List;
import java.util.Objects;

/**
 * Human-readable report: one aligned {@code label: value} line per field, the
 * rows of the goods table beneath, and a mark against anything that was worked
 * out rather than read.
 *
 * <p>The mark is the point. A report where every figure looks equally certain
 * asks the reader to check all of them or none; one that says which two were
 * inferred asks them to check two. That is the difference between a report that
 * gets verified and a report that gets trusted.</p>
 */
public final class PlainTextInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String LINE_SEPARATOR = "\n";
    private static final String RULE = "=".repeat(46);
    private static final String THIN_RULE = "-".repeat(46);

    /** Widths of the quantity and money columns in the goods table. */
    private static final int NUMBER_COLUMN = 12;

    private final MessageSource messages;
    private final ReportSettings settings;

    public PlainTextInvoiceReportFormatter(MessageSource messages) {
        this(messages, ReportDefaults.all());
    }

    public PlainTextInvoiceReportFormatter(MessageSource messages, ReportSettings settings) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        List<ExtractedField> fields = data.fields();

        StringBuilder report = new StringBuilder();
        report.append(RULE).append(LINE_SEPARATOR)
                .append(messages.get(MessageKeys.REPORT_HEADER)).append(LINE_SEPARATOR)
                .append(RULE).append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        int labelWidth = widestLabel(fields);
        boolean anyMarked = false;
        for (ExtractedField field : fields) {
            String label = messages.get(field.definition().labelKey());
            String value = field.valueOr(messages.get(MessageKeys.REPORT_MISSING_VALUE));
            report.append(pad(label, labelWidth)).append(" : ").append(value);
            if (marks(field)) {
                report.append(' ').append(messages.get(MessageKeys.REPORT_REVIEW_MARK));
                anyMarked = true;
            }
            report.append(LINE_SEPARATOR);
        }

        if (settings.includeLineItems() && data.hasLineItems()) {
            appendLineItems(report, data.lineItems());
        }

        report.append(LINE_SEPARATOR)
                .append(messages.get(MessageKeys.REPORT_FOOTER, data.recognizedCount(), fields.size()))
                .append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        if (anyMarked) {
            report.append(messages.get(MessageKeys.REPORT_REVIEW_HINT))
                    .append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }
        report.append(messages.get(MessageKeys.REPORT_HINT)).append(LINE_SEPARATOR);
        return report.toString();
    }

    private void appendLineItems(StringBuilder report, List<LineItem> items) {
        report.append(LINE_SEPARATOR).append(THIN_RULE).append(LINE_SEPARATOR)
                .append(messages.get(MessageKeys.REPORT_ITEMS_TITLE)).append(LINE_SEPARATOR)
                .append(THIN_RULE).append(LINE_SEPARATOR);

        int descriptionWidth = Math.max(
                messages.get(MessageKeys.REPORT_ITEMS_DESCRIPTION).length(),
                items.stream().mapToInt(item -> item.description().length()).max().orElse(0));

        report.append(pad(messages.get(MessageKeys.REPORT_ITEMS_DESCRIPTION), descriptionWidth))
                .append(padLeft(messages.get(MessageKeys.REPORT_ITEMS_QUANTITY), NUMBER_COLUMN))
                .append(padLeft(messages.get(MessageKeys.REPORT_ITEMS_UNIT_PRICE), NUMBER_COLUMN))
                .append(padLeft(messages.get(MessageKeys.REPORT_ITEMS_TOTAL), NUMBER_COLUMN))
                .append(LINE_SEPARATOR);

        String missing = messages.get(MessageKeys.REPORT_MISSING_VALUE);
        for (LineItem item : items) {
            report.append(pad(item.description(), descriptionWidth))
                    .append(padLeft(item.quantityOr(missing), NUMBER_COLUMN))
                    .append(padLeft(item.unitPriceOr(missing), NUMBER_COLUMN))
                    .append(padLeft(item.lineTotal(), NUMBER_COLUMN))
                    .append(LINE_SEPARATOR);
        }
    }

    /** True when a value is present and worth a second look. */
    private boolean marks(ExtractedField field) {
        return settings.showConfidence() && field.needsReview();
    }

    private int widestLabel(List<ExtractedField> fields) {
        return fields.stream()
                .map(field -> messages.get(field.definition().labelKey()))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    private static String padLeft(String value, int width) {
        return value.length() >= width ? " " + value : " ".repeat(width - value.length()) + value;
    }
}
