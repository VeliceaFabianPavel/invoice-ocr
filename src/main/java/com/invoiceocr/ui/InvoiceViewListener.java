package com.invoiceocr.ui;

/**
 * User intents a view can raise.
 *
 * <p>The view reports what the user wants, never what should happen next; that
 * decision belongs to the presenter.</p>
 */
public interface InvoiceViewListener {

    /** The user asked to pick and process a document. */
    void onLoadInvoiceRequested();

    /** The user asked to save the extracted data to a file. */
    void onExportRequested();

    /** The user asked to reset the panels. */
    void onClearRequested();
}
