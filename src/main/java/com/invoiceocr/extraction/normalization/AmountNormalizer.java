package com.invoiceocr.extraction.normalization;

import com.invoiceocr.extraction.text.Amounts;

/**
 * Normalises a monetary amount to {@code 1234.56}.
 *
 * <p>Delegates to {@link Amounts}, so the rule that decides which separator is
 * the decimal point lives in exactly one place: the same one the extractor uses
 * when it compares two amounts to find the largest.</p>
 */
public final class AmountNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        return Amounts.normalize(value);
    }
}
