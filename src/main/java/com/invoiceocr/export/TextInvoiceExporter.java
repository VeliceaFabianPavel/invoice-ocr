package com.invoiceocr.export;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.format.InvoiceReportFormatter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Turns any {@link InvoiceReportFormatter} into an {@link InvoiceExporter}.
 *
 * <p>This is why every text format costs one small formatter class and nothing
 * else: rendering stays in {@code format}, file handling stays here.</p>
 */
public final class TextInvoiceExporter implements InvoiceExporter {

    private final ExportFormat format;
    private final InvoiceReportFormatter formatter;
    private final Charset charset;

    public TextInvoiceExporter(ExportFormat format, InvoiceReportFormatter formatter) {
        this(format, formatter, StandardCharsets.UTF_8);
    }

    public TextInvoiceExporter(ExportFormat format, InvoiceReportFormatter formatter, Charset charset) {
        this.format = Objects.requireNonNull(format, "format");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.charset = Objects.requireNonNull(charset, "charset");
        if (format.binary()) {
            throw new IllegalArgumentException("Format " + format.id() + " is binary and needs its own exporter");
        }
    }

    @Override
    public ExportFormat format() {
        return format;
    }

    @Override
    public void write(InvoiceData data, OutputStream out) throws IOException {
        out.write(formatter.format(data).getBytes(charset));
    }
}
