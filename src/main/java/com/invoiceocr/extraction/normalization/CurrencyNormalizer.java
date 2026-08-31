package com.invoiceocr.extraction.normalization;

import java.util.Locale;

/**
 * Normalises a currency to its ISO 4217 code.
 *
 * <p>The only substantive case is {@code LEI}, which is what Romanian invoices
 * actually print and is not a currency code at all. Mapping it to {@code RON}
 * means an export can be imported by something that expects ISO codes, without
 * the user having to know that the two are the same thing.</p>
 */
public final class CurrencyNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        return "LEI".equals(code) ? "RON" : code;
    }
}
