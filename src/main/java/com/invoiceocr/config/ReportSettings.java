package com.invoiceocr.config;

/**
 * Settings that belong to what a report says.
 *
 * <p>Separate from {@link ExportSettings}, which is about where a report goes.
 * The two change for different reasons: turning the confidence markers off is a
 * preference about reading, not about saving.</p>
 */
public interface ReportSettings {

    /** Whether values found by a strategy that guesses are marked for the reader. */
    boolean showConfidence();

    /** Whether the rows of the goods table are included after the fields. */
    boolean includeLineItems();
}
