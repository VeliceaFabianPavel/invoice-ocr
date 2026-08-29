package com.invoiceocr.extraction.normalization;

import java.util.Locale;

/** Uppercases a value using a fixed locale, so behaviour never depends on the host. */
public final class UpperCaseNormalizer implements ValueNormalizer {

    private final Locale locale;

    public UpperCaseNormalizer() {
        this(Locale.ROOT);
    }

    public UpperCaseNormalizer(Locale locale) {
        this.locale = locale;
    }

    @Override
    public String normalize(String value) {
        return value == null ? "" : value.toUpperCase(locale);
    }
}
