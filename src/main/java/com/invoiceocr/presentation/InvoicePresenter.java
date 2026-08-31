package com.invoiceocr.presentation;

import com.invoiceocr.concurrent.TaskExecutor;
import com.invoiceocr.config.ExportSettings;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.exception.InvoiceOcrException;
import com.invoiceocr.export.InvoiceExportService;
import com.invoiceocr.format.InvoiceReportFormatter;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import com.invoiceocr.service.InvoiceRecognitionService;
import com.invoiceocr.ui.DocumentChooser;
import com.invoiceocr.ui.ExportChooser;
import com.invoiceocr.ui.ExportRequest;
import com.invoiceocr.ui.InvoiceView;
import com.invoiceocr.ui.InvoiceViewListener;
import com.invoiceocr.ui.UserNotifier;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Translates user intents into use-case calls and results into view updates.
 *
 * <p>Holds no state beyond its collaborators and touches no toolkit class, so it
 * can be exercised with a fake view, a fake chooser and a synchronous executor.</p>
 */
public final class InvoicePresenter implements InvoiceViewListener {

    private static final Logger LOG = System.getLogger(InvoicePresenter.class.getName());

    private final InvoiceView view;
    private final DocumentChooser documentChooser;
    private final ExportChooser exportChooser;
    private final InvoiceRecognitionService recognitionService;
    private final InvoiceExportService exportService;
    private final InvoiceReportFormatter reportFormatter;
    private final TaskExecutor taskExecutor;
    private final UserNotifier notifier;
    private final MessageSource messages;
    private final ExportSettings exportSettings;

    /** The most recent successful result, which is what an export writes. */
    private InvoiceData currentData;
    private String currentDocumentName = "invoice";

    public InvoicePresenter(InvoiceView view,
                            DocumentChooser documentChooser,
                            ExportChooser exportChooser,
                            InvoiceRecognitionService recognitionService,
                            InvoiceExportService exportService,
                            InvoiceReportFormatter reportFormatter,
                            TaskExecutor taskExecutor,
                            UserNotifier notifier,
                            MessageSource messages,
                            ExportSettings exportSettings) {
        this.view = Objects.requireNonNull(view, "view");
        this.documentChooser = Objects.requireNonNull(documentChooser, "documentChooser");
        this.exportChooser = Objects.requireNonNull(exportChooser, "exportChooser");
        this.recognitionService = Objects.requireNonNull(recognitionService, "recognitionService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.reportFormatter = Objects.requireNonNull(reportFormatter, "reportFormatter");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.exportSettings = Objects.requireNonNull(exportSettings, "exportSettings");
    }

    @Override
    public void onLoadInvoiceRequested() {
        Optional<Path> selection = documentChooser.choose();
        if (selection.isEmpty()) {
            return;
        }
        process(selection.get());
    }

    @Override
    public void onExportRequested() {
        if (currentData == null) {
            notifier.warning(messages.get(MessageKeys.WARNING_TITLE),
                    messages.get(MessageKeys.EXPORT_NOTHING_TO_EXPORT));
            return;
        }
        Optional<ExportRequest> request =
                exportChooser.choose(currentDocumentName, exportSettings.defaultFormat());
        if (request.isEmpty()) {
            return;
        }
        exportTo(request.get(), currentData);
    }

    private void exportTo(ExportRequest request, InvoiceData data) {
        view.setBusy(true);
        view.showStatus(messages.get(MessageKeys.STATUS_EXPORTING, request.target().getFileName()));

        taskExecutor.execute(
                () -> {
                    exportService.export(data, request.target(), request.format());
                    return request;
                },
                done -> {
                    view.setBusy(false);
                    view.showStatus(messages.get(MessageKeys.STATUS_EXPORTED, done.target().getFileName()));
                },
                failure -> {
                    LOG.log(Level.ERROR, "Export failed for " + request.target(), failure);
                    view.setBusy(false);
                    view.showStatus(messages.get(MessageKeys.STATUS_READY));
                    notifier.error(messages.get(MessageKeys.ERROR_TITLE), describe(failure));
                });
    }

    @Override
    public void onClearRequested() {
        currentData = null;
        view.clear();
        view.setExportEnabled(false);
        view.showStatus(messages.get(MessageKeys.STATUS_READY));
    }

    private void process(Path document) {
        view.setBusy(true);
        view.showStatus(messages.get(MessageKeys.STATUS_WORKING, document.getFileName()));

        taskExecutor.execute(
                () -> recognitionService.recognize(document),
                data -> onRecognized(document, data),
                failure -> onFailed(document, failure));
    }

    private void onRecognized(Path document, InvoiceData data) {
        currentData = data;
        currentDocumentName = baseNameOf(document);
        view.showRawText(data.source().value());
        view.showReport(reportFormatter.format(data));
        view.showStatus(statusFor(document, data));
        view.setBusy(false);
        view.setExportEnabled(true);
    }

    /**
     * The status line names the count of doubtful values when there are any.
     *
     * <p>It is the one place a user looks after a run, and "9 fields recognised,
     * 2 to check" turns the report from something to read into something to act
     * on. When nothing needs checking the shorter message is used, so the longer
     * one keeps meaning what it says.</p>
     */
    private String statusFor(Path document, InvoiceData data) {
        int toReview = data.needingReview().size();
        return toReview == 0
                ? messages.get(MessageKeys.STATUS_DONE, document.getFileName(), data.recognizedCount())
                : messages.get(MessageKeys.STATUS_DONE_WITH_REVIEW,
                        document.getFileName(), data.recognizedCount(), toReview);
    }

    private void onFailed(Path document, Throwable failure) {
        LOG.log(Level.ERROR, "Recognition failed for " + document, failure);
        view.showStatus(messages.get(MessageKeys.STATUS_FAILED, document.getFileName()));
        view.setBusy(false);
        notifier.error(messages.get(MessageKeys.ERROR_TITLE), describe(failure));
    }

    /** "factura-03.png" becomes "factura-03", which seeds the export file name. */
    private static String baseNameOf(Path document) {
        String name = document.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /**
     * Application failures already carry an operator-readable message; anything
     * else is a defect and gets a generic wrapper plus its type in the text.
     */
    private String describe(Throwable failure) {
        if (failure instanceof InvoiceOcrException) {
            return failure.getMessage();
        }
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return messages.get(MessageKeys.ERROR_UNEXPECTED, detail);
    }
}
