package com.invoiceocr.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.concurrent.DirectTaskExecutor;
import com.invoiceocr.config.ExportSettings;
import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.exception.ExportException;
import com.invoiceocr.exception.OcrExecutionException;
import com.invoiceocr.export.ExportFormat;
import com.invoiceocr.export.ExportFormats;
import com.invoiceocr.export.InvoiceExportService;
import com.invoiceocr.service.InvoiceRecognitionService;
import com.invoiceocr.support.RecordingInvoiceView;
import com.invoiceocr.support.RecordingUserNotifier;
import com.invoiceocr.support.TestMessageSource;
import com.invoiceocr.ui.DocumentChooser;
import com.invoiceocr.ui.ExportChooser;
import com.invoiceocr.ui.ExportRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InvoicePresenter")
class InvoicePresenterTest {

    private static final Path DOCUMENT = Path.of("factura-03.png");
    private static final Path TARGET = Path.of("out", "factura-03.pdf");

    private final RecordingInvoiceView view = new RecordingInvoiceView();
    private final RecordingUserNotifier notifier = new RecordingUserNotifier();
    private final RecordingExportService exportService = new RecordingExportService();

    private ExportChooser exportChooser = (name, format) -> Optional.empty();

    // ---------------------------------------------------------- recognition

    @Test
    @DisplayName("fills both panels and clears the busy state after a successful run")
    void rendersResult() {
        presenter(() -> Optional.of(DOCUMENT), path -> sampleData()).onLoadInvoiceRequested();

        assertEquals("Furnizor: ACME SRL", view.rawText());
        assertTrue(view.report().contains("ACME SRL"));
        assertEquals(List.of(true, false), view.busyStates());
        assertTrue(notifier.errors().isEmpty());
    }

    @Test
    @DisplayName("enables exporting only once there is something to export")
    void enablesExportAfterRecognition() {
        presenter(() -> Optional.of(DOCUMENT), path -> sampleData()).onLoadInvoiceRequested();

        assertEquals(List.of(true), view.exportStates());
    }

    @Test
    @DisplayName("does nothing at all when the user cancels the file dialog")
    void ignoresCancelledSelection() {
        presenter(Optional::empty, path -> sampleData()).onLoadInvoiceRequested();

        assertTrue(view.busyStates().isEmpty());
        assertEquals("", view.rawText());
    }

    @Test
    @DisplayName("shows the message of an application failure and leaves busy mode")
    void reportsApplicationFailures() {
        presenter(() -> Optional.of(DOCUMENT), path -> {
            throw new OcrExecutionException("tessdata directory not found: C:/nope");
        }).onLoadInvoiceRequested();

        assertEquals(List.of("tessdata directory not found: C:/nope"), notifier.errors());
        assertEquals(List.of(true, false), view.busyStates());
    }

    @Test
    @DisplayName("wraps an unexpected failure rather than leaking a raw stack trace message")
    void wrapsUnexpectedFailures() {
        presenter(() -> Optional.of(DOCUMENT), path -> {
            throw new IllegalStateException("null pointer somewhere");
        }).onLoadInvoiceRequested();

        assertEquals(1, notifier.errors().size());
        assertTrue(notifier.errors().get(0).startsWith("error.unexpected"));
    }

    // -------------------------------------------------------------- export

    @Test
    @DisplayName("warns instead of exporting when no invoice has been read yet")
    void refusesToExportNothing() {
        exportChooser = (name, format) -> {
            throw new AssertionError("the save dialog must not open with no data");
        };

        presenter(Optional::empty, path -> sampleData()).onExportRequested();

        assertEquals(List.of("export.nothingToExport"), notifier.warnings());
        assertTrue(exportService.calls().isEmpty());
    }

    @Test
    @DisplayName("exports the current result to the chosen file and format")
    void exportsCurrentResult() {
        exportChooser = (name, format) -> Optional.of(new ExportRequest(TARGET, ExportFormats.PDF));
        InvoicePresenter presenter = presenter(() -> Optional.of(DOCUMENT), path -> sampleData());

        presenter.onLoadInvoiceRequested();
        presenter.onExportRequested();

        assertEquals(1, exportService.calls().size());
        assertEquals(TARGET, exportService.calls().get(0).target());
        assertEquals(ExportFormats.PDF, exportService.calls().get(0).format());
        assertTrue(view.statuses().stream().anyMatch(status -> status.startsWith("status.exported")));
    }

    @Test
    @DisplayName("suggests a file name based on the invoice that was read")
    void suggestsFileNameFromDocument() {
        List<String> suggested = new ArrayList<>();
        exportChooser = (name, format) -> {
            suggested.add(name);
            return Optional.empty();
        };
        InvoicePresenter presenter = presenter(() -> Optional.of(DOCUMENT), path -> sampleData());

        presenter.onLoadInvoiceRequested();
        presenter.onExportRequested();

        assertEquals(List.of("factura-03"), suggested);
    }

    @Test
    @DisplayName("cancelling the save dialog writes nothing")
    void cancelledExportWritesNothing() {
        exportChooser = (name, format) -> Optional.empty();
        InvoicePresenter presenter = presenter(() -> Optional.of(DOCUMENT), path -> sampleData());

        presenter.onLoadInvoiceRequested();
        presenter.onExportRequested();

        assertTrue(exportService.calls().isEmpty());
    }

    @Test
    @DisplayName("reports a failed export and returns the view to a usable state")
    void reportsExportFailure() {
        exportChooser = (name, format) -> Optional.of(new ExportRequest(TARGET, ExportFormats.PDF));
        exportService.failWith(new ExportException("Could not write factura-03.pdf: access denied"));
        InvoicePresenter presenter = presenter(() -> Optional.of(DOCUMENT), path -> sampleData());

        presenter.onLoadInvoiceRequested();
        presenter.onExportRequested();

        assertEquals(List.of("Could not write factura-03.pdf: access denied"), notifier.errors());
        assertEquals(List.of(true, false, true, false), view.busyStates());
    }

    @Test
    @DisplayName("clearing forgets the result, so exporting is disabled again")
    void clearingDisablesExport() {
        InvoicePresenter presenter = presenter(() -> Optional.of(DOCUMENT), path -> sampleData());

        presenter.onLoadInvoiceRequested();
        presenter.onClearRequested();

        assertEquals(List.of(true, false), view.exportStates());
        assertEquals(1, view.clearCount());

        exportChooser = (name, format) -> {
            throw new AssertionError("the save dialog must not open after clearing");
        };
        presenter.onExportRequested();
        assertFalse(notifier.warnings().isEmpty());
    }

    // ------------------------------------------------------------ fixtures

    private InvoicePresenter presenter(DocumentChooser chooser, InvoiceRecognitionService service) {
        ExportSettings exportSettings = () -> ExportFormats.PDF;
        return new InvoicePresenter(
                view,
                chooser,
                (name, format) -> exportChooser.choose(name, format),
                service,
                exportService,
                data -> data.valueOf(InvoiceFields.SUPPLIER).orElse("N/A"),
                new DirectTaskExecutor(),
                notifier,
                new TestMessageSource(),
                exportSettings);
    }

    private static InvoiceData sampleData() {
        return InvoiceData.of(
                RecognizedText.of("Furnizor: ACME SRL"),
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")));
    }

    /** Records what it was asked to export, and can be told to fail. */
    private static final class RecordingExportService implements InvoiceExportService {

        private final List<ExportRequest> calls = new ArrayList<>();
        private RuntimeException failure;

        void failWith(RuntimeException exception) {
            this.failure = exception;
        }

        List<ExportRequest> calls() {
            return List.copyOf(calls);
        }

        @Override
        public void export(InvoiceData data, Path target, ExportFormat format) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new ExportRequest(target, format));
        }

        @Override
        public List<ExportFormat> supportedFormats() {
            return ExportFormats.ALL;
        }
    }
}
