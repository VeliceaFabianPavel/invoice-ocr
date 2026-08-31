package com.invoiceocr.recognition;

import com.invoiceocr.domain.InvoiceData;
import java.util.Objects;

/**
 * What one pass made of the page.
 *
 * <p>The pass is kept alongside its result because the merge needs to explain
 * itself: when two passes disagree, the report and the logs should be able to
 * say which reading was taken and why, and a bare {@link InvoiceData} cannot.</p>
 *
 * @param pass  the attempt that produced this
 * @param data  the fields it read
 * @param milliseconds how long it took, for the diagnostic log
 */
public record PassOutcome(RecognitionPass pass, InvoiceData data, long milliseconds) {

    public PassOutcome {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(data, "data");
    }

    /**
     * A single number for how well this pass did, used only to pick which pass's
     * raw text is shown to the user.
     *
     * <p>Fields found matter more than confidence in them, so the count leads and
     * the mean confidence breaks the ties. A pass that read nine fields hesitantly
     * is a better transcription of the page than one that read three with total
     * conviction.</p>
     */
    public double quality() {
        return data.recognizedCount() + data.averageConfidence();
    }

    @Override
    public String toString() {
        return pass.name() + ": " + data.recognizedCount() + " fields, "
                + String.format(java.util.Locale.ROOT, "%.2f", data.averageConfidence())
                + " mean confidence, " + milliseconds + " ms";
    }
}
