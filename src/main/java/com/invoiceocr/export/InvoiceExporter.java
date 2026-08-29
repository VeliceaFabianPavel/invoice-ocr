package com.invoiceocr.export;

import com.invoiceocr.domain.InvoiceData;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes invoice data in one particular format.
 *
 * <p>Writing to a stream rather than a file keeps every exporter testable
 * without touching the disk, and leaves file creation, overwriting and error
 * translation to a single place: {@link DefaultInvoiceExportService}.</p>
 */
public interface InvoiceExporter {

    /** The format this exporter produces. */
    ExportFormat format();

    /**
     * Writes {@code data} to {@code out}. The stream is not closed here; the
     * caller owns it.
     */
    void write(InvoiceData data, OutputStream out) throws IOException;
}
