package com.invoiceocr.extraction.normalization;

import com.invoiceocr.extraction.text.OcrDigits;
import java.util.Objects;

/**
 * Turns the letters Tesseract mistook for digits back into digits.
 *
 * <p>The patterns accept those letters so a damaged value is still found; this
 * is where the damage is undone, once, at the point where the value is being
 * made presentable.</p>
 */
public final class DigitRepairNormalizer implements ValueNormalizer {

    public enum Scope {
        /** Every character: for values that are entirely numeric. */
        WHOLE_VALUE,
        /** Only wholly digit-shaped runs: for values that mix a series with a number. */
        NUMERIC_RUNS
    }

    private final Scope scope;

    public DigitRepairNormalizer(Scope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return scope == Scope.WHOLE_VALUE
                ? OcrDigits.repair(value)
                : OcrDigits.repairNumericRuns(value);
    }
}
