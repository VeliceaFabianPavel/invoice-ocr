package com.invoiceocr.export;

import com.invoiceocr.domain.InvoiceData;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes extracted invoice data to a file.
 *
 * <p>The presentation layer calls only this: which exporter handles which
 * format, and how the file is written safely, are details below it.</p>
 */
public interface InvoiceExportService {

    /**
     * @throws com.invoiceocr.exception.ExportException if the file cannot be written
     */
    void export(InvoiceData data, Path target, ExportFormat format);

    /** Formats that actually have an exporter registered, in offer order. */
    List<ExportFormat> supportedFormats();
}
