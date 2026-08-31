package com.invoiceocr.app;

import com.invoiceocr.concurrent.SwingTaskExecutor;
import com.invoiceocr.concurrent.TaskExecutor;
import com.invoiceocr.config.ChainedConfigurationSource;
import com.invoiceocr.config.ConfigurationBackedExportSettings;
import com.invoiceocr.config.ConfigurationBackedOcrSettings;
import com.invoiceocr.config.ConfigurationBackedReportSettings;
import com.invoiceocr.config.ConfigurationSource;
import com.invoiceocr.config.ExportSettings;
import com.invoiceocr.config.EnvironmentConfigurationSource;
import com.invoiceocr.config.LocaleProvider;
import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.config.PropertiesConfigurationSource;
import com.invoiceocr.config.ReportSettings;
import com.invoiceocr.config.SystemPropertiesConfigurationSource;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
import com.invoiceocr.extraction.items.LineItemRefinement;
import com.invoiceocr.extraction.items.TableLineItemExtractor;
import com.invoiceocr.extraction.validation.ArithmeticRefinement;
import com.invoiceocr.extraction.validation.DateRefinement;
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
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.image.ImageIoDocumentLoader;
import com.invoiceocr.ocr.OcrEngineFactory;
import com.invoiceocr.ocr.tesseract.TesseractOcrEngineFactory;
import com.invoiceocr.presentation.InvoicePresenter;
import com.invoiceocr.recognition.InvoiceDataMerger;
import com.invoiceocr.recognition.RecognitionPlan;
import com.invoiceocr.service.InvoiceRecognitionService;
import com.invoiceocr.service.MultiPassInvoiceRecognitionService;
import com.invoiceocr.ui.DocumentChooser;
import com.invoiceocr.ui.ExportChooser;
import com.invoiceocr.ui.InvoiceView;
import com.invoiceocr.ui.UserNotifier;
import com.invoiceocr.ui.swing.SwingDocumentChooser;
import com.invoiceocr.ui.swing.SwingExportChooser;
import com.invoiceocr.ui.swing.SwingInvoiceView;
import com.invoiceocr.ui.swing.SwingUserNotifier;
import java.nio.file.Path;
import java.util.ArrayList;
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
        ReportSettings reportSettings = new ConfigurationBackedReportSettings(configuration);
        Locale locale = LocaleProvider.fromConfiguration(configuration).locale();
        MessageSource messages = messageSource(locale);

        InvoiceRecognitionService service = recognitionService(settings);

        InvoiceExportService exportService = exportService(messages, reportSettings);
        ExportSettings exportSettings = new ConfigurationBackedExportSettings(configuration);

        SwingInvoiceView view = new SwingInvoiceView(messages);
        DocumentChooser chooser = new SwingDocumentChooser(settings, messages, view::rootComponent);
        ExportChooser exportChooser =
                new SwingExportChooser(exportService.supportedFormats(), messages, view::rootComponent);
        UserNotifier notifier = new SwingUserNotifier(view::rootComponent);

        view.addListener(new InvoicePresenter(
                view, chooser, exportChooser, service, exportService,
                reportFormatter(messages, reportSettings), taskExecutor(), notifier,
                messages, exportSettings));
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

    protected OcrEngineFactory ocrEngineFactory(OcrSettings settings) {
        return new TesseractOcrEngineFactory(settings);
    }

    /**
     * The pipeline: decode once, then read the page as many times as it takes.
     *
     * <p>The image is loaded a single time and each pass prepares its own copy,
     * so a second reading costs one OCR call rather than a second decode. The
     * merge that follows runs the refinements again over the combined fields,
     * because a total from one pass beside a net amount from another is a pair
     * neither pass ever saw.</p>
     */
    protected InvoiceRecognitionService recognitionService(OcrSettings settings) {
        return new MultiPassInvoiceRecognitionService(
                documentLoader(settings),
                ocrEngineFactory(settings),
                invoiceParser(settings),
                RecognitionPlan.forSettings(settings),
                new InvoiceDataMerger(refinements(settings)),
                settings.targetConfidence());
    }

    protected InvoiceParser invoiceParser(OcrSettings settings) {
        return new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider(), refinements(settings));
    }

    /**
     * The post-parse passes, in the order they have to run.
     *
     * <p>The table comes first because it can supply a net amount, the dates
     * next because they depend on nothing else, and the arithmetic last because
     * it is the one that has to see everything the others produced.</p>
     */
    protected List<InvoiceRefinement> refinements(OcrSettings settings) {
        List<InvoiceRefinement> refinements = new ArrayList<>(3);
        if (settings.lineItemsEnabled()) {
            refinements.add(new LineItemRefinement(new TableLineItemExtractor()));
        }
        refinements.add(new DateRefinement());
        refinements.add(new ArithmeticRefinement());
        return List.copyOf(refinements);
    }

    protected InvoiceReportFormatter reportFormatter(MessageSource messages, ReportSettings settings) {
        return new PlainTextInvoiceReportFormatter(messages, settings);
    }

    /**
     * Registers one exporter per format.
     *
     * <p>Readable formats carry the raw OCR text as well as the fields; data
     * formats carry the fields alone, so a machine reading them gets a clean
     * record. The order here is the order the save dialog offers.</p>
     */
    protected InvoiceExportService exportService(MessageSource messages, ReportSettings settings) {
        InvoiceReportFormatter plainText = new RawTextAppendingFormatter(
                new PlainTextInvoiceReportFormatter(messages, settings), messages);

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
