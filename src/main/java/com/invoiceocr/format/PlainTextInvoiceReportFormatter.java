package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.List;
import java.util.Objects;

/** Human-readable report: one aligned {@code label: value} line per field. */
public final class PlainTextInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String LINE_SEPARATOR = "\n";
    private static final String RULE = "=".repeat(46);

    private final MessageSource messages;

    public PlainTextInvoiceReportFormatter(MessageSource messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
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
        for (ExtractedField field : fields) {
            String label = messages.get(field.definition().labelKey());
            String value = field.valueOr(messages.get(MessageKeys.REPORT_MISSING_VALUE));
            report.append(pad(label, labelWidth)).append(" : ").append(value).append(LINE_SEPARATOR);
        }

        report.append(LINE_SEPARATOR)
                .append(messages.get(MessageKeys.REPORT_FOOTER, data.recognizedCount(), fields.size()))
                .append(LINE_SEPARATOR).append(LINE_SEPARATOR)
                .append(messages.get(MessageKeys.REPORT_HINT)).append(LINE_SEPARATOR);
        return report.toString();
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
}
