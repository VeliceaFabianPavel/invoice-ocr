package com.invoiceocr.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.exception.ExportException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("DefaultInvoiceExportService")
class DefaultInvoiceExportServiceTest {

    @TempDir
    Path directory;

    private final InvoiceExportService service = new DefaultInvoiceExportService(List.of(
            new TextInvoiceExporter(ExportFormats.TXT, data -> "written"),
            new FailingExporter()));

    @Test
    @DisplayName("writes the file the exporter produced")
    void writesTheFile() throws IOException {
        Path target = directory.resolve("report.txt");

        service.export(sample(), target, ExportFormats.TXT);

        assertEquals("written", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("creates missing folders on the way to the target")
    void createsParentDirectories() {
        Path target = directory.resolve("exports/2026/report.txt");

        service.export(sample(), target, ExportFormats.TXT);

        assertTrue(Files.exists(target));
    }

    @Test
    @DisplayName("refuses a format that has no exporter registered")
    void rejectsUnknownFormat() {
        ExportException failure = assertThrows(ExportException.class,
                () -> service.export(sample(), directory.resolve("x.csv"), ExportFormats.CSV));

        assertTrue(failure.getMessage().contains("csv"));
    }

    @Test
    @DisplayName("leaves an existing file untouched when the export fails halfway")
    void keepsThePreviousFileOnFailure() throws IOException {
        Path target = directory.resolve("report.json");
        Files.writeString(target, "previous good export");

        assertThrows(ExportException.class, () -> service.export(sample(), target, ExportFormats.JSON));

        assertEquals("previous good export", Files.readString(target));
    }

    @Test
    @DisplayName("does not leave a partial file behind after a failure")
    void removesPartialFiles() throws IOException {
        Path target = directory.resolve("report.json");

        assertThrows(ExportException.class, () -> service.export(sample(), target, ExportFormats.JSON));

        try (var entries = Files.list(directory)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    @DisplayName("reports the formats it can actually write, in offer order")
    void reportsSupportedFormats() {
        assertEquals(List.of(ExportFormats.TXT, ExportFormats.JSON), service.supportedFormats());
    }

    private static InvoiceData sample() {
        return InvoiceData.of(RecognizedText.of("raw"),
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")));
    }

    /** Stands in for an exporter that dies partway through writing. */
    private static final class FailingExporter implements InvoiceExporter {

        @Override
        public ExportFormat format() {
            return ExportFormats.JSON;
        }

        @Override
        public void write(InvoiceData data, OutputStream out) throws IOException {
            out.write("half a fi".getBytes(StandardCharsets.UTF_8));
            throw new IOException("disk full");
        }
    }
}
