package com.invoiceocr.ui;

/**
 * Everything the application needs from a user interface, expressed without a
 * single reference to Swing.
 *
 * <p>A console or JavaFX front end implements this same interface; the presenter
 * does not change.</p>
 */
public interface InvoiceView {

    /** Registers the component that reacts to user intents. */
    void addListener(InvoiceViewListener listener);

    /** Shows the unmodified OCR output. */
    void showRawText(String rawText);

    /** Shows the formatted, structured report. */
    void showReport(String report);

    /** Shows a short status line, e.g. the file being processed. */
    void showStatus(String status);

    /** Enables or disables the busy indicator and the actions that must not overlap. */
    void setBusy(boolean busy);

    /** Enables the export action, which is only meaningful once there is data. */
    void setExportEnabled(boolean enabled);

    /** Empties both panels. */
    void clear();

    /** Makes the view visible to the user. */
    void display();
}
