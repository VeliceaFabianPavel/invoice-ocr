package com.invoiceocr.extraction.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Normalizers")
class NormalizersTest {

    @Test
    @DisplayName("text() collapses OCR whitespace and trims edge punctuation")
    void cleansFreeText() {
        assertEquals("SC EXEMPLU SRL", Normalizers.text().normalize("  SC   EXEMPLU    SRL |  "));
    }

    @Test
    @DisplayName("code() uppercases and compacts a document number")
    void cleansCode() {
        assertEquals("ABC-1024", Normalizers.code().normalize(" abc-1024. "));
    }

    @Test
    @DisplayName("fiscalCode() drops separators and keeps the RO prefix")
    void cleansFiscalCode() {
        assertEquals("RO12345678", Normalizers.fiscalCode().normalize("ro 12.345.678"));
    }

    @Test
    @DisplayName("amount() strips a trailing separator left behind by OCR")
    void cleansAmount() {
        assertEquals("1234", Normalizers.amount().normalize("1.234."));
    }

    @Test
    @DisplayName("chaining is left to right")
    void chainsInOrder() {
        ValueNormalizer chain = new WhitespaceNormalizer().andThen(new UpperCaseNormalizer());
        assertEquals("A B", chain.normalize("  a   b "));
    }
}
