package com.invoiceocr.extraction.normalization;

/**
 * Strips punctuation that OCR commonly drags in at the edges of a capture,
 * such as the trailing pipe of a table border or a stray dash.
 */
public final class TrailingPunctuationNormalizer implements ValueNormalizer {

    private static final String EDGE_CHARACTERS = "[\\s.,;:|_\\-]+";

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^" + EDGE_CHARACTERS, "")
                .replaceAll(EDGE_CHARACTERS + "$", "");
    }
}
