package com.invoiceocr.format;

import com.invoiceocr.domain.InvoiceData;

/**
 * Renders structured invoice data as text for a specific audience.
 *
 * <p>The view knows how to display a string; this interface decides what that
 * string says. Swapping plain text for JSON, CSV or HTML is a wiring change.</p>
 */
@FunctionalInterface
public interface InvoiceReportFormatter {

    String format(InvoiceData data);
}
