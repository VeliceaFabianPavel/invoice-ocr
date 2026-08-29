package com.invoiceocr.i18n;

/** Keys of every user-visible message; the values live in {@code messages*.properties}. */
public final class MessageKeys {

    public static final String APP_TITLE = "app.title";

    public static final String ACTION_LOAD = "action.load";
    public static final String ACTION_LOAD_TOOLTIP = "action.load.tooltip";
    public static final String ACTION_EXPORT = "action.export";
    public static final String ACTION_EXPORT_TOOLTIP = "action.export.tooltip";
    public static final String ACTION_CLEAR = "action.clear";

    public static final String PANEL_RAW_TEXT = "panel.rawText";
    public static final String PANEL_STRUCTURED = "panel.structured";

    public static final String CHOOSER_TITLE = "chooser.title";
    public static final String CHOOSER_FILTER = "chooser.filter";
    public static final String CHOOSER_EXPORT_TITLE = "chooser.export.title";
    public static final String CHOOSER_EXPORT_APPROVE = "chooser.export.approve";
    public static final String CHOOSER_OVERWRITE_TITLE = "chooser.overwrite.title";
    public static final String CHOOSER_OVERWRITE_QUESTION = "chooser.overwrite.question";

    public static final String STATUS_READY = "status.ready";
    public static final String STATUS_WORKING = "status.working";
    public static final String STATUS_DONE = "status.done";
    public static final String STATUS_FAILED = "status.failed";
    public static final String STATUS_EXPORTING = "status.exporting";
    public static final String STATUS_EXPORTED = "status.exported";

    public static final String REPORT_HEADER = "report.header";
    public static final String REPORT_FOOTER = "report.footer";
    public static final String REPORT_HINT = "report.hint";
    public static final String REPORT_MISSING_VALUE = "report.missingValue";
    public static final String REPORT_RAW_TEXT = "report.rawText";
    public static final String REPORT_COLUMN_FIELD = "report.column.field";
    public static final String REPORT_COLUMN_VALUE = "report.column.value";
    public static final String REPORT_PAGE = "report.page";

    /** Names of the export formats, shown in the save dialog. */
    public static final String FORMAT_PDF = "format.pdf";
    public static final String FORMAT_TXT = "format.txt";
    public static final String FORMAT_MARKDOWN = "format.md";
    public static final String FORMAT_HTML = "format.html";
    public static final String FORMAT_JSON = "format.json";
    public static final String FORMAT_XML = "format.xml";
    public static final String FORMAT_CSV = "format.csv";

    public static final String ERROR_TITLE = "error.title";
    public static final String ERROR_UNEXPECTED = "error.unexpected";
    public static final String WARNING_TITLE = "warning.title";
    public static final String EXPORT_NOTHING_TO_EXPORT = "export.nothingToExport";

    private MessageKeys() {
        throw new AssertionError("No instances");
    }
}
