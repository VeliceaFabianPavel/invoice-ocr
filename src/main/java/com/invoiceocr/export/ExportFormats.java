package com.invoiceocr.export;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalog of the formats this application can export.
 *
 * <p>Two groups, and the difference is deliberate:</p>
 * <ul>
 *   <li><strong>Readable</strong> formats (TXT, Markdown, HTML, PDF) carry the
 *       six fields <em>and</em> the raw OCR text, because a person reading the
 *       file may need to check what the engine actually saw.</li>
 *   <li><strong>Data</strong> formats (JSON, XML, CSV) carry the fields only,
 *       because they are read by another program that wants a clean record.</li>
 * </ul>
 */
public final class ExportFormats {

    public static final ExportFormat TXT      = ExportFormat.text("txt", "format.txt", "txt");
    public static final ExportFormat MARKDOWN = ExportFormat.text("md", "format.md", "md");
    public static final ExportFormat HTML     = ExportFormat.text("html", "format.html", "html");
    public static final ExportFormat JSON     = ExportFormat.text("json", "format.json", "json");
    public static final ExportFormat XML      = ExportFormat.text("xml", "format.xml", "xml");
    public static final ExportFormat CSV      = ExportFormat.text("csv", "format.csv", "csv");
    public static final ExportFormat PDF      = new ExportFormat("pdf", "format.pdf", "pdf", true);

    /** Every built-in format, in the order the save dialog offers them. */
    public static final List<ExportFormat> ALL =
            List.of(PDF, TXT, MARKDOWN, HTML, JSON, XML, CSV);

    /** Looks a format up by its id, case-insensitively. */
    public static Optional<ExportFormat> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(format -> format.id().equals(needle)).findFirst();
    }

    private ExportFormats() {
        throw new AssertionError("No instances");
    }
}
