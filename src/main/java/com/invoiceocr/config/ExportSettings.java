package com.invoiceocr.config;

import com.invoiceocr.export.ExportFormat;

/**
 * Settings that belong to exporting.
 *
 * <p>Kept apart from {@link OcrSettings} so neither interface grows into a
 * bag of unrelated options: a class that only exports never has to see the
 * page segmentation mode.</p>
 */
public interface ExportSettings {

    /** The format the save dialog opens on. */
    ExportFormat defaultFormat();
}
