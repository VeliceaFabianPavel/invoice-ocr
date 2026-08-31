package com.invoiceocr.extraction.normalization;

import com.invoiceocr.extraction.validation.Ibans;

/**
 * Normalises a bank account to the grouped IBAN form, {@code RO49 AAAA 1B31 …}.
 *
 * <p>Grouped rather than compact because an IBAN is copied by eye far more often
 * than by machine, and four-character groups are what makes that survivable. The
 * checksum is computed on the compact form regardless, so the spaces cost
 * nothing.</p>
 */
public final class IbanNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        return value == null ? "" : Ibans.format(value);
    }
}
