package com.invoiceocr.ui;

import com.invoiceocr.export.ExportFormat;
import java.util.Optional;

/**
 * Asks the user where to export and in which format.
 *
 * <p>Returning the format alongside the path keeps the choice in one place:
 * the dialog knows the user picked "PDF document", so the presenter never has
 * to guess a format from a file extension.</p>
 */
@FunctionalInterface
public interface ExportChooser {

    /**
     * @param suggestedName file name without extension, typically derived from
     *                      the invoice that was processed
     * @param preselected   the format the dialog should open on
     * @return the chosen destination, or empty when the user cancelled
     */
    Optional<ExportRequest> choose(String suggestedName, ExportFormat preselected);
}
