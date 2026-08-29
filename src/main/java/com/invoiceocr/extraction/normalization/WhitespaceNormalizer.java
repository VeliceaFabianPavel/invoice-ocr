package com.invoiceocr.extraction.normalization;

/** Collapses runs of whitespace (including OCR line noise) into single spaces and trims. */
public final class WhitespaceNormalizer implements ValueNormalizer {

    @Override
    public String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
