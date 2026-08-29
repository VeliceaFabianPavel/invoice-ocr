package com.invoiceocr.export;

import java.util.Locale;
import java.util.Objects;

/**
 * One file format the extracted data can be written as.
 *
 * <p>A value rather than an enum, so a new format is added by registering
 * another exporter instead of editing a fixed list.</p>
 *
 * @param id        stable identifier, also the value accepted by the
 *                  {@code export.defaultFormat} setting
 * @param labelKey  i18n key for the name shown in the save dialog
 * @param extension file extension, without the dot
 * @param binary    {@code true} when the payload is not text
 */
public record ExportFormat(String id, String labelKey, String extension, boolean binary) {

    public ExportFormat {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(extension, "extension");
        if (id.isBlank() || extension.isBlank()) {
            throw new IllegalArgumentException("Format id and extension must not be blank");
        }
    }

    public static ExportFormat text(String id, String labelKey, String extension) {
        return new ExportFormat(id, labelKey, extension, false);
    }

    /** Whether {@code fileName} already ends with this format's extension. */
    public boolean matches(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    /** Returns {@code fileName} with this format's extension, adding it when absent. */
    public String withExtension(String fileName) {
        return matches(fileName) ? fileName : fileName + "." + extension;
    }
}
