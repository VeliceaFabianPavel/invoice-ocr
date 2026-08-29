package com.invoiceocr.app;

import com.invoiceocr.concurrent.SwingTaskExecutor;
import com.invoiceocr.concurrent.TaskExecutor;
import com.invoiceocr.config.ChainedConfigurationSource;
import com.invoiceocr.config.ConfigurationBackedExportSettings;
import com.invoiceocr.config.ConfigurationBackedOcrSettings;
import com.invoiceocr.config.ConfigurationSource;
import com.invoiceocr.config.ExportSettings;
import com.invoiceocr.config.EnvironmentConfigurationSource;
import com.invoiceocr.config.LocaleProvider;
import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.config.PropertiesConfigurationSource;
import com.invoiceocr.config.SystemPropertiesConfigurationSource;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
import com.invoiceocr.export.DefaultInvoiceExportService;
import com.invoiceocr.export.ExportFormats;
import com.invoiceocr.export.InvoiceExportService;
import com.invoiceocr.export.InvoiceExporter;
import com.invoiceocr.export.PdfInvoiceExporter;
import com.invoiceocr.export.TextInvoiceExporter;
import com.invoiceocr.extraction.rules.RomanianInvoiceRuleProvider;
import com.invoiceocr.format.CsvInvoiceReportFormatter;
import com.invoiceocr.format.HtmlInvoiceReportFormatter;
import com.invoiceocr.format.InvoiceReportFormatter;
import com.invoiceocr.format.JsonInvoiceReportFormatter;
import com.invoiceocr.format.MarkdownInvoiceReportFormatter;
import com.invoiceocr.format.PlainTextInvoiceReportFormatter;
import com.invoiceocr.format.RawTextAppendingFormatter;
import com.invoiceocr.format.XmlInvoiceReportFormatter;
import com.invoiceocr.i18n.MessageSource;
import com.invoiceocr.i18n.ResourceBundleMessageSource;
import com.invoiceocr.image.CompositeImagePreprocessor;
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.image.GrayscalePreprocessor;
import com.invoiceocr.image.ImageIoDocumentLoader;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.image.UpscalePreprocessor;
import com.invoiceocr.ocr.OcrEngineFactory;
import com.invoiceocr.ocr.tesseract.TesseractOcrEngineFactory;
import com.invoiceocr.presentation.InvoicePresenter;
import com.invoiceocr.service.DefaultInvoiceRecognitionService;
import com.invoiceocr.service.InvoiceRecognitionService;
import com.invoiceocr.ui.DocumentChooser;
import com.invoiceocr.ui.ExportChooser;
import com.invoiceocr.ui.InvoiceView;
import com.invoiceocr.ui.UserNotifier;
import com.invoiceocr.ui.swing.SwingDocumentChooser;
import com.invoiceocr.ui.swing.SwingExportChooser;
import com.invoiceocr.ui.swing.SwingInvoiceView;
import com.invoiceocr.ui.swing.SwingUserNotifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The composition root: the one place that knows which implementation plays
 * which role.
 *
 * <p>Every other class receives its collaborators through the constructor and
 * never reaches out for one, which is what keeps the layers substitutable. Each
 * step below is a protected factory method, so a variant assembler (headless,
 * a different OCR backend, a different language) overrides one method rather
 * than rewriting the wiring.</p>
 */
public class ApplicationAssembler {

    private static final String CONFIG_RESOURCE = "application.properties";
    private static final String EXTERNAL_CONFIG_FILE = "invoice-ocr.properties";
    private static final String MESSAGES_BUNDLE = "messages";

    /** Builds the object graph and returns the view, ready to be displayed. */
    public InvoiceView assemble() {
        ConfigurationSource configuration = configurationSource();
        OcrSettings settings = new ConfigurationBackedOcrSettings(configuration);
        Locale locale = LocaleProvider.fromConfiguration(configuration).locale();
        MessageSource messages = messageSource(locale);

        InvoiceRecognitionService service = new DefaultInvoiceRecognitionService(
                documentLoader(settings),
                imagePreprocessor(settings),
                ocrEngineFactory(settings),
                invoiceParser());

        InvoiceExportService exportService = exportService(messages);
        ExportSettings exportSettings = new ConfigurationBackedExportSettings(configuration);

        SwingInvoiceView view = new SwingInvoiceView(messages);
        DocumentChooser chooser = new SwingDocumentChooser(settings, messages, view::rootComponent);
        ExportChooser exportChooser =
                new SwingExportChooser(exportService.supportedFormats(), messages, view::rootComponent);
        UserNotifier notifier = new SwingUserNotifier(view::rootComponent);

        view.addListener(new InvoicePresenter(
                view, chooser, exportChooser, service, exportService,
                reportFormatter(messages), taskExecutor(), notifier, messages, exportSettings));
        return view;
    }

    /**
     * Precedence, highest first: {@code -D} system properties, environment
     * variables, an {@code invoice-ocr.properties} next to the jar, then the
     * bundled defaults. Deployment overrides never require a rebuild.
     */
    protected ConfigurationSource configurationSource() {
        return ChainedConfigurationSource.of(
                new SystemPropertiesConfigurationSource(),
                new EnvironmentConfigurationSource(),
                PropertiesConfigurationSource.fromFile(Path.of(EXTERNAL_CONFIG_FILE)),
                PropertiesConfigurationSource.fromClasspath(CONFIG_RESOURCE));
    }

    protected MessageSource messageSource(Locale locale) {
        return ResourceBundleMessageSource.forBaseName(MESSAGES_BUNDLE, locale);
    }

    protected DocumentLoader documentLoader(OcrSettings settings) {
        return new ImageIoDocumentLoader(settings);
    }

    protected ImagePreprocessor imagePreprocessor(OcrSettings settings) {
        if (!settings.preprocessingEnabled()) {
            return ImagePreprocessor.identity();
        }
        return CompositeImagePreprocessor.of(
                new UpscalePreprocessor(settings.minimumWidth()),
                new GrayscalePreprocessor());
    }

    protected OcrEngineFactory ocrEngineFactory(OcrSettings settings) {
        return new TesseractOcrEngineFactory(settings);
    }

    protected InvoiceParser invoiceParser() {
        return new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider());
    }

    protected InvoiceReportFormatter reportFormatter(MessageSource messages) {
        return new PlainTextInvoiceReportFormatter(messages);
    }

    /**
     * Registers one exporter per format.
     *
     * <p>Readable formats carry the raw OCR text as well as the fields; data
     * formats carry the fields alone, so a machine reading them gets a clean
     * record. The order here is the order the save dialog offers.</p>
     */
    protected InvoiceExportService exportService(MessageSource messages) {
        InvoiceReportFormatter plainText =
                new RawTextAppendingFormatter(new PlainTextInvoiceReportFormatter(messages), messages);

        List<InvoiceExporter> exporters = List.of(
                new PdfInvoiceExporter(messages),
                new TextInvoiceExporter(ExportFormats.TXT, plainText),
                new TextInvoiceExporter(ExportFormats.MARKDOWN, new MarkdownInvoiceReportFormatter(messages)),
                new TextInvoiceExporter(ExportFormats.HTML, new HtmlInvoiceReportFormatter(messages)),
                new TextInvoiceExporter(ExportFormats.JSON, new JsonInvoiceReportFormatter()),
                new TextInvoiceExporter(ExportFormats.XML, new XmlInvoiceReportFormatter()),
                new TextInvoiceExporter(ExportFormats.CSV, new CsvInvoiceReportFormatter()));

        return new DefaultInvoiceExportService(exporters);
    }

    protected TaskExecutor taskExecutor() {
        return new SwingTaskExecutor();
    }
}
