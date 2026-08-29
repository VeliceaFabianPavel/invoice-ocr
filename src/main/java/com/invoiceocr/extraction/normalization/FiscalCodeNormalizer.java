package com.invoiceocr.extraction.normalization;

import com.invoiceocr.extraction.text.OcrDigits;
import java.util.Locale;

/**
 * Normalises a Romanian fiscal code to {@code RO12345678}.
 *
 * <p>The country prefix is handled apart from the digits on purpose: repairing
 * the whole value would read the {@code O} of {@code RO} as a zero. So the
 * prefix is recognised first, and only what follows it is repaired.</p>
 */
public final class FiscalCodeNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
        if (compact.length() >= 2 && compact.charAt(0) == 'R'
                && (compact.charAt(1) == 'O' || compact.charAt(1) == '0')) {
            return "RO" + OcrDigits.repair(compact.substring(2));
        }
        return OcrDigits.repair(compact);
    }
}
