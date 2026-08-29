package com.invoiceocr.format;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.Objects;

/**
 * Appends the raw OCR text to whatever another formatter produced.
 *
 * <p>A decorator rather than an option on the plain-text formatter: the panel
 * on screen shows the summary alone, while an exported file is read away from
 * the application and benefits from carrying the evidence with it.</p>
 */
public final class RawTextAppendingFormatter implements InvoiceReportFormatter {

    private static final String LINE_SEPARATOR = "\n";
    private static final String RULE = "=".repeat(46);

    private final InvoiceReportFormatter delegate;
    private final MessageSource messages;

    public RawTextAppendingFormatter(InvoiceReportFormatter delegate, MessageSource messages) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String format(InvoiceData data) {
        String report = delegate.format(data);
        if (data.source().isBlank()) {
            return report;
        }
        return report
                + LINE_SEPARATOR
                + RULE + LINE_SEPARATOR
                + messages.get(MessageKeys.REPORT_RAW_TEXT) + LINE_SEPARATOR
                + RULE + LINE_SEPARATOR + LINE_SEPARATOR
                + data.source().value() + LINE_SEPARATOR;
    }
}
