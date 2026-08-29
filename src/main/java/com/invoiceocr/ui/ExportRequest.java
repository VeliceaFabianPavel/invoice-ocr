package com.invoiceocr.ui;

import com.invoiceocr.export.ExportFormat;
import java.nio.file.Path;
import java.util.Objects;

/** What the user chose in the save dialog: a destination and a format. */
public record ExportRequest(Path target, ExportFormat format) {

    public ExportRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(format, "format");
    }
}
