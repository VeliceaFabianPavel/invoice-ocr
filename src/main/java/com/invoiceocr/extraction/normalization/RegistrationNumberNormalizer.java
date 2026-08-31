package com.invoiceocr.extraction.normalization;

import java.util.Locale;

/**
 * Normalises a trade-register number to {@code J40/1122/2015}.
 *
 * <p>OCR routinely puts spaces around the slashes and occasionally after the
 * county letter. All of them are removed, because the number is an identifier
 * and its spacing carries no meaning.</p>
 */
public final class RegistrationNumberNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\s.]", "").toUpperCase(Locale.ROOT);
    }
}
